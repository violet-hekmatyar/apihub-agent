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
    if ($null -eq $stream) {
        return $null
    }
    $reader = New-Object System.IO.StreamReader($stream)
    $content = $reader.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }
    return $content | ConvertFrom-Json
}

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Url,

        [hashtable]$Headers = @{},

        [string]$Body = $null,

        [int]$TimeoutSec = 15
    )

    $params = @{
        Uri = $Url
        Method = $Method
        Headers = $Headers
        TimeoutSec = $TimeoutSec
    }

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
            if ($null -ne $json) {
                return $json
            }
        }
        throw
    }
}

function Write-Fail {
    param(
        [string]$Name,
        [string]$Endpoint,
        [string]$Expected,
        $Actual
    )

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

Write-Host "Alert Evaluator smoke target: server=$BaseUrl mock-provider=$MockProviderBaseUrl"

$backendHealthName = "apihub-server health"
try {
    $response = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/health"
    $ok = Assert-Equal -Name $backendHealthName -Endpoint "GET /api/health" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-Equal -Name $backendHealthName -Endpoint "GET /api/health" -Field "data.status" -Expected "UP" -Actual $response.data.status) -and $ok
    if ($ok) { Write-Pass $backendHealthName }
}
catch {
    Write-Fail -Name $backendHealthName -Endpoint "GET /api/health" -Expected "reachable JSON response" -Actual $_.Exception.Message
}

$mockHealthName = "mock-provider health"
try {
    $response = Invoke-JsonRequest -Method "GET" -Url "$MockProviderBaseUrl/mock-provider/health"
    $ok = Assert-Equal -Name $mockHealthName -Endpoint "GET /mock-provider/health" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-Equal -Name $mockHealthName -Endpoint "GET /mock-provider/health" -Field "data.status" -Expected "UP" -Actual $response.data.status) -and $ok
    if ($ok) { Write-Pass $mockHealthName }
}
catch {
    Write-Fail -Name $mockHealthName -Endpoint "GET /mock-provider/health" -Expected "reachable JSON response" -Actual $_.Exception.Message
}

if ($script:Failed -gt 0) {
    Write-Host "Alert Evaluator smoke failed before scenario setup." -ForegroundColor Red
    exit 1
}

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
        randomSeed = 20260623
    }
    sampleLimit = 10
} | ConvertTo-Json -Depth 8 -Compress

$scenarioRunId = $null
$createName = "scenario runner create lecture peak"
try {
    $response = Invoke-JsonRequest -Method "POST" -Url "$MockProviderBaseUrl/mock-provider/scenario-runs" -Body $scenarioBody -TimeoutSec 20
    $scenarioRunId = $response.data.scenarioRunId
    $ok = Assert-Equal -Name $createName -Endpoint "POST /mock-provider/scenario-runs" -Field "code" -Expected 202 -Actual $response.code
    $ok = (Assert-Equal -Name $createName -Endpoint "POST /mock-provider/scenario-runs" -Field "data.status" -Expected "RUNNING" -Actual $response.data.status) -and $ok
    $ok = (Assert-NotNull -Name $createName -Endpoint "POST /mock-provider/scenario-runs" -Field "data.scenarioRunId" -Actual $scenarioRunId) -and $ok
    if ($ok) { Write-Pass $createName }
}
catch {
    Write-Fail -Name $createName -Endpoint "POST /mock-provider/scenario-runs" -Expected "created scenario run" -Actual $_.Exception.Message
}

if ([string]::IsNullOrWhiteSpace($scenarioRunId)) {
    Write-Host "Alert Evaluator smoke failed: scenarioRunId is empty." -ForegroundColor Red
    exit 1
}

$finalStatus = $null
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 1
    $statusResponse = Invoke-JsonRequest -Method "GET" -Url "$MockProviderBaseUrl/mock-provider/scenario-runs/$scenarioRunId"
    $finalStatus = $statusResponse.data.status
    if ($finalStatus -eq "COMPLETED" -or $finalStatus -eq "FAILED" -or $finalStatus -eq "CANCELLED" -or $finalStatus -eq "TIMEOUT") {
        break
    }
}

if ($finalStatus -eq "COMPLETED") {
    Write-Pass "scenario runner completed"
} else {
    Write-Fail -Name "scenario runner completed" -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId" -Expected "status=COMPLETED" -Actual "status=$finalStatus"
}

$resultName = "scenario runner result"
try {
    $resultResponse = Invoke-JsonRequest -Method "GET" -Url "$MockProviderBaseUrl/mock-provider/scenario-runs/$scenarioRunId/result"
    $lectureCount = [int]$resultResponse.data.resultSummary.apiDistribution.LECTURE_REGISTER
    $ok = Assert-Equal -Name $resultName -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId/result" -Field "code" -Expected 200 -Actual $resultResponse.code
    $ok = (Assert-GreaterThan -Name $resultName -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId/result" -Field "LECTURE_REGISTER count" -Threshold 19 -Actual $lectureCount) -and $ok
    if ($ok) { Write-Pass $resultName }
}
catch {
    Write-Fail -Name $resultName -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId/result" -Expected "valid scenario result" -Actual $_.Exception.Message
}

$windowEnd = (Get-Date).AddSeconds(15)
$evaluateStart = Get-SmokeTime $windowStart
$evaluateEnd = Get-SmokeTime $windowEnd

$evaluateBody = [ordered]@{
    startTime = $evaluateStart
    endTime = $evaluateEnd
    mode = "DEV_SHORT_WINDOW"
    windowSeconds = 30
    scenarioRunId = $scenarioRunId
    apiCode = "LECTURE_REGISTER"
    forceRebuild = $true
} | ConvertTo-Json -Depth 8 -Compress

$firstAlertType = $null
$evaluateName = "alert evaluate dev short window"
try {
    $evaluateResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/alerts/evaluate" -Body $evaluateBody -TimeoutSec 30
    $ok = Assert-Equal -Name $evaluateName -Endpoint "POST /api/dev/alerts/evaluate" -Field "code" -Expected 200 -Actual $evaluateResponse.code
    $ok = (Assert-Equal -Name $evaluateName -Endpoint "POST /api/dev/alerts/evaluate" -Field "data.mode" -Expected "DEV_SHORT_WINDOW" -Actual $evaluateResponse.data.mode) -and $ok
    $ok = (Assert-Equal -Name $evaluateName -Endpoint "POST /api/dev/alerts/evaluate" -Field "data.windowSeconds" -Expected 30 -Actual ([int]$evaluateResponse.data.windowSeconds)) -and $ok
    $ok = (Assert-GreaterThan -Name $evaluateName -Endpoint "POST /api/dev/alerts/evaluate" -Field "data.evaluatedWindowCount" -Threshold 0 -Actual ([int]$evaluateResponse.data.evaluatedWindowCount)) -and $ok
    $ok = (Assert-GreaterThan -Name $evaluateName -Endpoint "POST /api/dev/alerts/evaluate" -Field "data.createdAlertCount" -Threshold 0 -Actual ([int]$evaluateResponse.data.createdAlertCount)) -and $ok
    $items = @($evaluateResponse.data.items)
    $firstAlertType = $items[0].alertType
    $ok = (Assert-NotNull -Name $evaluateName -Endpoint "POST /api/dev/alerts/evaluate" -Field "items[0].alertType" -Actual $firstAlertType) -and $ok
    $ok = (Assert-Equal -Name $evaluateName -Endpoint "POST /api/dev/alerts/evaluate" -Field "items[0].extraInfo.statSource" -Expected "GATEWAY_LOG_SHORT_WINDOW" -Actual $items[0].extraInfo.statSource) -and $ok
    if ($ok) { Write-Pass $evaluateName }
}
catch {
    Write-Fail -Name $evaluateName -Endpoint "POST /api/dev/alerts/evaluate" -Expected "created alert_event rows" -Actual $_.Exception.Message
}

if ([string]::IsNullOrWhiteSpace($firstAlertType)) {
    Write-Host "Alert Evaluator smoke failed: firstAlertType is empty." -ForegroundColor Red
    exit 1
}

$queryBody = [ordered]@{
    apiCode = "LECTURE_REGISTER"
    startTime = $evaluateStart
    endTime = $evaluateEnd
    alertType = $firstAlertType
    status = "OPEN"
    limit = 20
} | ConvertTo-Json -Depth 6 -Compress

$queryName = "queryAlertEvents reads evaluated alert"
try {
    $toolResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/tools/queryAlertEvents" -Headers @{ "X-Demo-User-Id" = "1" } -Body $queryBody -TimeoutSec 30
    $alerts = @($toolResponse.data.data.alerts)
    $matched = @($alerts | Where-Object { $_.extraInfo.scenarioRunId -eq $scenarioRunId -and $_.extraInfo.mode -eq "DEV_SHORT_WINDOW" })
    $ok = Assert-Equal -Name $queryName -Endpoint "POST /api/dev/tools/queryAlertEvents" -Field "code" -Expected 200 -Actual $toolResponse.code
    $ok = (Assert-Equal -Name $queryName -Endpoint "POST /api/dev/tools/queryAlertEvents" -Field "data.success" -Expected $true -Actual $toolResponse.data.success) -and $ok
    $ok = (Assert-Equal -Name $queryName -Endpoint "POST /api/dev/tools/queryAlertEvents" -Field "data.toolName" -Expected "queryAlertEvents" -Actual $toolResponse.data.toolName) -and $ok
    $ok = (Assert-GreaterThan -Name $queryName -Endpoint "POST /api/dev/tools/queryAlertEvents" -Field "matched evaluated alerts" -Threshold 0 -Actual $matched.Count) -and $ok
    if ($ok) { Write-Pass $queryName }
}
catch {
    Write-Fail -Name $queryName -Endpoint "POST /api/dev/tools/queryAlertEvents" -Expected "ToolResult with evaluated alert" -Actual $_.Exception.Message
}

if ($script:Failed -gt 0) {
    Write-Host "Alert Evaluator smoke failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "[PASS] Scenario Runner -> Gateway Invoke -> gateway_log -> Alert Evaluator -> alert_event -> queryAlertEvents" -ForegroundColor Green
exit 0
