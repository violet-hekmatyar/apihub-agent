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

Write-Host "Agent Diagnosis Evidence smoke target: server=$BaseUrl mock-provider=$MockProviderBaseUrl"

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
    Write-Host "Agent Diagnosis Evidence smoke failed before scenario setup." -ForegroundColor Red
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
        randomSeed = 20260624
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
    Write-Host "Agent Diagnosis Evidence smoke failed: scenarioRunId is empty." -ForegroundColor Red
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

$aggregateName = "stats aggregate"
try {
    $aggregateResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/stats/aggregate" -Body $aggregateBody -TimeoutSec 30
    $ok = Assert-Equal -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "code" -Expected 200 -Actual $aggregateResponse.code
    $ok = (Assert-GreaterThan -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "data.aggregatedRows" -Threshold 0 -Actual ([int]$aggregateResponse.data.aggregatedRows)) -and $ok
    if ($ok) { Write-Pass $aggregateName }
}
catch {
    Write-Fail -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Expected "aggregated stats response" -Actual $_.Exception.Message
}

$alertBody = [ordered]@{
    startTime = $start
    endTime = $end
    mode = "DEV_SHORT_WINDOW"
    windowSeconds = 30
    scenarioRunId = $scenarioRunId
    apiCode = "LECTURE_REGISTER"
    forceRebuild = $true
} | ConvertTo-Json -Depth 8 -Compress

$alertName = "alert evaluate"
try {
    $alertResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/alerts/evaluate" -Body $alertBody -TimeoutSec 30
    $ok = Assert-Equal -Name $alertName -Endpoint "POST /api/dev/alerts/evaluate" -Field "code" -Expected 200 -Actual $alertResponse.code
    $ok = (Assert-GreaterThan -Name $alertName -Endpoint "POST /api/dev/alerts/evaluate" -Field "data.createdAlertCount" -Threshold 0 -Actual ([int]$alertResponse.data.createdAlertCount)) -and $ok
    if ($ok) { Write-Pass $alertName }
}
catch {
    Write-Fail -Name $alertName -Endpoint "POST /api/dev/alerts/evaluate" -Expected "created alert_event rows" -Actual $_.Exception.Message
}

$alertQueryBody = [ordered]@{
    apiCode = "LECTURE_REGISTER"
    startTime = $start
    endTime = $end
    status = "OPEN"
    limit = 20
} | ConvertTo-Json -Depth 6 -Compress

$alertQueryName = "queryAlertEvents sees alert"
try {
    $toolResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/tools/queryAlertEvents" -Headers @{ "X-Demo-User-Id" = "1" } -Body $alertQueryBody -TimeoutSec 30
    $ok = Assert-Equal -Name $alertQueryName -Endpoint "POST /api/dev/tools/queryAlertEvents" -Field "code" -Expected 200 -Actual $toolResponse.code
    $ok = (Assert-GreaterThan -Name $alertQueryName -Endpoint "POST /api/dev/tools/queryAlertEvents" -Field "data.data.totalMatched" -Threshold 0 -Actual ([int]$toolResponse.data.data.totalMatched)) -and $ok
    if ($ok) { Write-Pass $alertQueryName }
}
catch {
    Write-Fail -Name $alertQueryName -Endpoint "POST /api/dev/tools/queryAlertEvents" -Expected "ToolResult with alerts" -Actual $_.Exception.Message
}

$diagnoseBody = [ordered]@{
    apiCode = "LECTURE_REGISTER"
    startTime = $start
    endTime = $end
    scenarioRunId = $scenarioRunId
    diagnosisMode = "DETERMINISTIC"
    forceRebuild = $true
} | ConvertTo-Json -Depth 8 -Compress

$reportId = $null
$diagnoseName = "agent diagnose"
try {
    $diagnoseResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/agent/diagnose" -Headers @{ "X-Demo-User-Id" = "1" } -Body $diagnoseBody -TimeoutSec 30
    $reportId = $diagnoseResponse.data.reportId
    $ok = Assert-Equal -Name $diagnoseName -Endpoint "POST /api/dev/agent/diagnose" -Field "code" -Expected 200 -Actual $diagnoseResponse.code
    $ok = (Assert-Equal -Name $diagnoseName -Endpoint "POST /api/dev/agent/diagnose" -Field "data.status" -Expected "COMPLETED" -Actual $diagnoseResponse.data.status) -and $ok
    $ok = (Assert-NotNull -Name $diagnoseName -Endpoint "POST /api/dev/agent/diagnose" -Field "data.reportId" -Actual $reportId) -and $ok
    $ok = (Assert-NotNull -Name $diagnoseName -Endpoint "POST /api/dev/agent/diagnose" -Field "data.riskLevel" -Actual $diagnoseResponse.data.riskLevel) -and $ok
    $ok = (Assert-NotNull -Name $diagnoseName -Endpoint "POST /api/dev/agent/diagnose" -Field "data.summary" -Actual $diagnoseResponse.data.summary) -and $ok
    $ok = (Assert-GreaterThan -Name $diagnoseName -Endpoint "POST /api/dev/agent/diagnose" -Field "data.evidenceCount" -Threshold 2 -Actual ([int]$diagnoseResponse.data.evidenceCount)) -and $ok
    $ok = (Assert-GreaterThan -Name $diagnoseName -Endpoint "POST /api/dev/agent/diagnose" -Field "data.toolCallCount" -Threshold 2 -Actual ([int]$diagnoseResponse.data.toolCallCount)) -and $ok
    if ($ok) { Write-Pass $diagnoseName }
}
catch {
    Write-Fail -Name $diagnoseName -Endpoint "POST /api/dev/agent/diagnose" -Expected "completed diagnosis report" -Actual $_.Exception.Message
}

if ($null -eq $reportId) {
    Write-Host "Agent Diagnosis Evidence smoke failed: reportId is empty." -ForegroundColor Red
    exit 1
}

$reportName = "agent report detail"
try {
    $reportResponse = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/dev/agent/reports/$reportId" -TimeoutSec 30
    $evidenceTypes = @($reportResponse.data.evidenceItems | ForEach-Object { $_.evidenceType })
    $toolNames = @($reportResponse.data.toolCallTraces | ForEach-Object { $_.toolName })
    $ok = Assert-Equal -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "code" -Expected 200 -Actual $reportResponse.code
    $ok = (Assert-Equal -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "data.report.reportId" -Expected ([int64]$reportId) -Actual ([int64]$reportResponse.data.report.reportId)) -and $ok
    $ok = (Assert-Contains -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "evidenceTypes" -Expected "ALERT_EVENT" -ActualList $evidenceTypes) -and $ok
    $ok = (Assert-Contains -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "evidenceTypes" -Expected "API_CALL_STAT" -ActualList $evidenceTypes) -and $ok
    $ok = (Assert-Contains -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "evidenceTypes" -Expected "GATEWAY_LOG_SAMPLE" -ActualList $evidenceTypes) -and $ok
    $ok = (Assert-Contains -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "toolNames" -Expected "queryAlertEvents" -ActualList $toolNames) -and $ok
    $ok = (Assert-Contains -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "toolNames" -Expected "queryApiCallStats" -ActualList $toolNames) -and $ok
    $ok = (Assert-Contains -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Field "toolNames" -Expected "queryGatewayLogs" -ActualList $toolNames) -and $ok
    if ($ok) { Write-Pass $reportName }
}
catch {
    Write-Fail -Name $reportName -Endpoint "GET /api/dev/agent/reports/$reportId" -Expected "report, evidence, and tool traces" -Actual $_.Exception.Message
}

if ($script:Failed -gt 0) {
    Write-Host "Agent Diagnosis Evidence smoke failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "[PASS] Scenario Runner -> Gateway Invoke -> gateway_log -> Stats Aggregator -> api_call_stat_hourly -> Alert Evaluator -> alert_event -> Agent Diagnosis -> agent_report/evidence_item/tool_call_trace" -ForegroundColor Green
exit 0
