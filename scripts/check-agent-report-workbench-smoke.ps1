param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$MockProviderBaseUrl = "http://localhost:8090"
)

$ErrorActionPreference = "Stop"
$script:Failed = 0
$BaseUrl = $BaseUrl.TrimEnd("/")
$MockProviderBaseUrl = $MockProviderBaseUrl.TrimEnd("/")

function Convert-ErrorResponseToJson {
    param($Response)
    $stream = $Response.GetResponseStream()
    if ($null -eq $stream) { return $null }
    $reader = New-Object System.IO.StreamReader($stream)
    $content = $reader.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($content)) { return $null }
    return $content | ConvertFrom-Json
}

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [int]$TimeoutSec = 15
    )
    $params = @{ Uri = $Url; Method = $Method; Headers = $Headers; TimeoutSec = $TimeoutSec }
    if (-not [string]::IsNullOrEmpty($Body)) {
        $params.ContentType = "application/json"
        $params.Body = $Body
    }
    try {
        return Invoke-RestMethod @params
    }
    catch {
        if ($_.Exception.Response) {
            $json = Convert-ErrorResponseToJson -Response $_.Exception.Response
            if ($null -ne $json) { return $json }
        }
        throw
    }
}

function Write-Fail {
    param([string]$Name, [string]$Endpoint, [string]$Expected, $Actual)
    $script:Failed++
    Write-Host "[FAIL] $Name" -ForegroundColor Red
    Write-Host "       endpoint: $Endpoint"
    Write-Host "       expected: $Expected"
    Write-Host "       actual:   $Actual"
}

function Write-Pass {
    param([string]$Name)
    Write-Host "[PASS] $Name" -ForegroundColor Green
}

function Assert-Equal {
    param($Name, $Endpoint, $Field, $Expected, $Actual)
    if ($Expected -ne $Actual) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field=$Expected" -Actual "$Field=$Actual"
        return $false
    }
    return $true
}

function Assert-GreaterThan {
    param($Name, $Endpoint, $Field, $Threshold, $Actual)
    if ($Actual -le $Threshold) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field>$Threshold" -Actual "$Field=$Actual"
        return $false
    }
    return $true
}

function Assert-Contains {
    param($Name, $Endpoint, $Field, $Expected, $ActualList)
    if ($ActualList -notcontains $Expected) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field contains $Expected" -Actual ($ActualList -join ",")
        return $false
    }
    return $true
}

function Assert-TextContains {
    param($Name, $Endpoint, $Expected, $Actual)
    if ($Actual -notlike "*$Expected*") {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "text contains $Expected" -Actual "missing"
        return $false
    }
    return $true
}

function Assert-NotNull {
    param($Name, $Endpoint, $Field, $Actual)
    if ($null -eq $Actual) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field exists" -Actual "$Field=<null>"
        return $false
    }
    return $true
}

function Get-SmokeTime {
    param([datetime]$Value)
    return $Value.ToString("yyyy-MM-dd HH:mm:ss")
}

Write-Host "Agent Report Workbench smoke target: server=$BaseUrl mock-provider=$MockProviderBaseUrl"

try {
    $response = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/health"
    $ok = Assert-Equal -Name "apihub-server health" -Endpoint "GET /api/health" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-Equal -Name "apihub-server health" -Endpoint "GET /api/health" -Field "data.status" -Expected "UP" -Actual $response.data.status) -and $ok
    if ($ok) { Write-Pass "apihub-server health" }
}
catch { Write-Fail -Name "apihub-server health" -Endpoint "GET /api/health" -Expected "reachable JSON response" -Actual $_.Exception.Message }

try {
    $response = Invoke-JsonRequest -Method "GET" -Url "$MockProviderBaseUrl/mock-provider/health"
    $ok = Assert-Equal -Name "mock-provider health" -Endpoint "GET /mock-provider/health" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-Equal -Name "mock-provider health" -Endpoint "GET /mock-provider/health" -Field "data.status" -Expected "UP" -Actual $response.data.status) -and $ok
    if ($ok) { Write-Pass "mock-provider health" }
}
catch { Write-Fail -Name "mock-provider health" -Endpoint "GET /mock-provider/health" -Expected "reachable JSON response" -Actual $_.Exception.Message }

if ($script:Failed -gt 0) { exit 1 }

$windowStart = (Get-Date).AddSeconds(-10)
$scenarioBody = [ordered]@{
    scenarioId = "LECTURE_REGISTER_PEAK"
    targetGatewayBaseUrl = $BaseUrl
    loadProfile = [ordered]@{
        logicalDurationSeconds = 30
        timeScale = 30
        rampUpSeconds = 5
        steadySeconds = 20
        rampDownSeconds = 5
        baseRps = 3.0
        peakRps = 12.0
        maxConcurrency = 8
        randomSeed = 20260625
    }
    sampleLimit = 10
} | ConvertTo-Json -Depth 8 -Compress

$scenarioRunId = $null
try {
    $response = Invoke-JsonRequest -Method "POST" -Url "$MockProviderBaseUrl/mock-provider/scenario-runs" -Body $scenarioBody -TimeoutSec 20
    $scenarioRunId = $response.data.scenarioRunId
    $ok = Assert-Equal -Name "scenario runner create lecture peak" -Endpoint "POST /mock-provider/scenario-runs" -Field "code" -Expected 202 -Actual $response.code
    $ok = (Assert-NotNull -Name "scenario runner create lecture peak" -Endpoint "POST /mock-provider/scenario-runs" -Field "data.scenarioRunId" -Actual $scenarioRunId) -and $ok
    if ($ok) { Write-Pass "scenario runner create lecture peak" }
}
catch { Write-Fail -Name "scenario runner create lecture peak" -Endpoint "POST /mock-provider/scenario-runs" -Expected "created scenario run" -Actual $_.Exception.Message }

$statusResponse = $null
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 1
    $statusResponse = Invoke-JsonRequest -Method "GET" -Url "$MockProviderBaseUrl/mock-provider/scenario-runs/$scenarioRunId"
    if ($statusResponse.data.status -eq "COMPLETED" -or $statusResponse.data.status -eq "FAILED" -or $statusResponse.data.status -eq "CANCELLED" -or $statusResponse.data.status -eq "TIMEOUT") { break }
}
if ($statusResponse.data.status -eq "COMPLETED") {
    Write-Pass "scenario runner completed"
} else {
    Write-Fail -Name "scenario runner completed" -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId" -Expected "status=COMPLETED" -Actual $statusResponse.data.status
}

$windowEnd = (Get-Date).AddSeconds(15)
$start = Get-SmokeTime $windowStart
$end = Get-SmokeTime $windowEnd

$aggregateBody = [ordered]@{
    startTime = $start
    endTime = $end
    scenarioRunId = $scenarioRunId
    apiCode = "LECTURE_REGISTER"
    forceRebuild = $true
} | ConvertTo-Json -Depth 6 -Compress

try {
    $response = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/stats/aggregate" -Body $aggregateBody -TimeoutSec 30
    $ok = Assert-Equal -Name "stats aggregate" -Endpoint "POST /api/dev/stats/aggregate" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-GreaterThan -Name "stats aggregate" -Endpoint "POST /api/dev/stats/aggregate" -Field "data.aggregatedRows" -Threshold 0 -Actual ([int]$response.data.aggregatedRows)) -and $ok
    if ($ok) { Write-Pass "stats aggregate" }
}
catch { Write-Fail -Name "stats aggregate" -Endpoint "POST /api/dev/stats/aggregate" -Expected "aggregated stats" -Actual $_.Exception.Message }

$alertBody = [ordered]@{
    startTime = $start
    endTime = $end
    mode = "DEV_SHORT_WINDOW"
    windowSeconds = 30
    scenarioRunId = $scenarioRunId
    apiCode = "LECTURE_REGISTER"
    forceRebuild = $true
} | ConvertTo-Json -Depth 8 -Compress

try {
    $response = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/alerts/evaluate" -Body $alertBody -TimeoutSec 30
    $ok = Assert-Equal -Name "alert evaluate" -Endpoint "POST /api/dev/alerts/evaluate" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-GreaterThan -Name "alert evaluate" -Endpoint "POST /api/dev/alerts/evaluate" -Field "data.createdAlertCount" -Threshold 0 -Actual ([int]$response.data.createdAlertCount)) -and $ok
    if ($ok) { Write-Pass "alert evaluate" }
}
catch { Write-Fail -Name "alert evaluate" -Endpoint "POST /api/dev/alerts/evaluate" -Expected "created alerts" -Actual $_.Exception.Message }

$diagnoseBody = [ordered]@{
    apiCode = "LECTURE_REGISTER"
    startTime = $start
    endTime = $end
    scenarioRunId = $scenarioRunId
    diagnosisMode = "DETERMINISTIC"
    forceRebuild = $true
} | ConvertTo-Json -Depth 8 -Compress

$reportId = $null
$riskLevel = $null
try {
    $response = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/agent/diagnose" -Headers @{ "X-Demo-User-Id" = "1" } -Body $diagnoseBody -TimeoutSec 30
    $reportId = $response.data.reportId
    $riskLevel = $response.data.riskLevel
    $ok = Assert-Equal -Name "agent diagnose" -Endpoint "POST /api/dev/agent/diagnose" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-NotNull -Name "agent diagnose" -Endpoint "POST /api/dev/agent/diagnose" -Field "data.reportId" -Actual $reportId) -and $ok
    if ($ok) { Write-Pass "agent diagnose" }
}
catch { Write-Fail -Name "agent diagnose" -Endpoint "POST /api/dev/agent/diagnose" -Expected "completed diagnosis" -Actual $_.Exception.Message }

try {
    $response = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/dev/agent/reports?pageNo=1&pageSize=10&apiCode=LECTURE_REGISTER" -TimeoutSec 30
    $ids = @($response.data.items | ForEach-Object { [int64]$_.reportId })
    $ok = Assert-Equal -Name "report list" -Endpoint "GET /api/dev/agent/reports" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-GreaterThan -Name "report list" -Endpoint "GET /api/dev/agent/reports" -Field "data.total" -Threshold 0 -Actual ([int]$response.data.total)) -and $ok
    $ok = (Assert-Contains -Name "report list" -Endpoint "GET /api/dev/agent/reports" -Field "reportIds" -Expected ([int64]$reportId) -ActualList $ids) -and $ok
    if ($ok) { Write-Pass "report list" }
}
catch { Write-Fail -Name "report list" -Endpoint "GET /api/dev/agent/reports" -Expected "list contains reportId=$reportId" -Actual $_.Exception.Message }

try {
    $response = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/dev/agent/reports/$reportId" -TimeoutSec 30
    $evidenceTypes = @($response.data.evidenceItems | ForEach-Object { $_.evidenceType })
    $toolNames = @($response.data.toolCallTraces | ForEach-Object { $_.toolName })
    $ok = Assert-Equal -Name "report detail" -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-GreaterThan -Name "report detail" -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "evidenceItems.Count" -Threshold 2 -Actual @($response.data.evidenceItems).Count) -and $ok
    $ok = (Assert-GreaterThan -Name "report detail" -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "toolCallTraces.Count" -Threshold 2 -Actual @($response.data.toolCallTraces).Count) -and $ok
    $ok = (Assert-Contains -Name "report detail" -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "evidenceTypes" -Expected "ALERT_EVENT" -ActualList $evidenceTypes) -and $ok
    $ok = (Assert-Contains -Name "report detail" -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "evidenceTypes" -Expected "API_CALL_STAT" -ActualList $evidenceTypes) -and $ok
    $ok = (Assert-Contains -Name "report detail" -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "evidenceTypes" -Expected "GATEWAY_LOG_SAMPLE" -ActualList $evidenceTypes) -and $ok
    $ok = (Assert-Contains -Name "report detail" -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "toolNames" -Expected "queryAlertEvents" -ActualList $toolNames) -and $ok
    if ($ok) { Write-Pass "report detail" }
}
catch { Write-Fail -Name "report detail" -Endpoint "GET /api/dev/agent/reports/$reportId" -Expected "detail with evidence and traces" -Actual $_.Exception.Message }

try {
    $raw = (curl.exe -s -i --max-time 30 "$BaseUrl/api/dev/agent/reports/$reportId/html") -join "`r`n"
    $headerEnd = $raw.IndexOf("`r`n`r`n")
    if ($headerEnd -lt 0) {
        throw "curl response did not contain HTTP headers"
    }
    $headers = $raw.Substring(0, $headerEnd)
    $html = $raw.Substring($headerEnd + 4)
    $statusLine = ($headers -split "`r`n")[0]
    $contentType = (($headers -split "`r`n") | Where-Object { $_ -like "Content-Type:*" } | Select-Object -First 1)
    $ok = Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "200" -Actual $statusLine
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "text/html" -Actual $contentType) -and $ok
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "API-HUB Agent" -Actual $html) -and $ok
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "$reportId" -Actual $html) -and $ok
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "LECTURE_REGISTER" -Actual $html) -and $ok
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "$riskLevel" -Actual $html) -and $ok
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "ALERT_EVENT" -Actual $html) -and $ok
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "queryAlertEvents" -Actual $html) -and $ok
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "Microsoft Edge" -Actual $html) -and $ok
    $ok = (Assert-TextContains -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "@page" -Actual $html) -and $ok
    if ($ok) { Write-Pass "report html" }
}
catch { Write-Fail -Name "report html" -Endpoint "GET /api/dev/agent/reports/$reportId/html" -Expected "print-ready HTML" -Actual $_.Exception.Message }

if ($script:Failed -gt 0) {
    Write-Host "Agent Report Workbench smoke failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "[PASS] Agent Diagnosis -> report list -> report detail -> report HTML -> Edge print to PDF ready" -ForegroundColor Green
exit 0
