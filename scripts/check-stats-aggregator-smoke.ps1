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

function Get-SmokeHourFloor {
    param([datetime]$Value)
    return $Value.ToString("yyyy-MM-dd HH:00:00")
}

function Sum-ObjectValues {
    param($Object)

    $sum = 0
    if ($null -eq $Object) {
        return 0
    }
    foreach ($property in $Object.PSObject.Properties) {
        $sum += [int]$property.Value
    }
    return $sum
}

Write-Host "Stats Aggregator smoke target: server=$BaseUrl mock-provider=$MockProviderBaseUrl"

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
    Write-Host "Stats Aggregator smoke failed before scenario setup." -ForegroundColor Red
    exit 1
}

$windowStart = (Get-Date).AddMinutes(-2)
$scenarioBody = [ordered]@{
    scenarioId = "LECTURE_REGISTER_PEAK"
    targetGatewayBaseUrl = $BaseUrl
    loadProfile = [ordered]@{
        logicalDurationSeconds = 10
        timeScale = 10
        rampUpSeconds = 2
        steadySeconds = 6
        rampDownSeconds = 2
        baseRps = 1.0
        peakRps = 3.0
        maxConcurrency = 3
        randomSeed = 20260622
    }
    sampleLimit = 10
} | ConvertTo-Json -Depth 8 -Compress

$scenarioRunId = $null
$createName = "scenario runner create lecture peak"
try {
    $response = Invoke-JsonRequest -Method "POST" -Url "$MockProviderBaseUrl/mock-provider/scenario-runs" -Body $scenarioBody
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
    Write-Host "Stats Aggregator smoke failed: scenarioRunId is empty." -ForegroundColor Red
    exit 1
}

$finalStatus = $null
for ($i = 0; $i -lt 60; $i++) {
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
$coreApis = @()
try {
    $resultResponse = Invoke-JsonRequest -Method "GET" -Url "$MockProviderBaseUrl/mock-provider/scenario-runs/$scenarioRunId/result"
    $totalSent = [int]$resultResponse.data.totalSentRequests
    $ok = Assert-Equal -Name $resultName -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId/result" -Field "code" -Expected 200 -Actual $resultResponse.code
    $ok = (Assert-GreaterThan -Name $resultName -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId/result" -Field "data.totalSentRequests" -Threshold 0 -Actual $totalSent) -and $ok
    $apiSum = Sum-ObjectValues $resultResponse.data.resultSummary.apiDistribution
    $ok = (Assert-Equal -Name $resultName -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId/result" -Field "sum(apiDistribution)" -Expected $totalSent -Actual $apiSum) -and $ok
    $coreApis = @($resultResponse.data.resultSummary.apiDistribution.PSObject.Properties | ForEach-Object { $_.Name })
    if ($coreApis -notcontains "LECTURE_REGISTER") {
        Write-Fail -Name $resultName -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId/result" -Expected "apiDistribution contains LECTURE_REGISTER" -Actual ($coreApis -join ",")
        $ok = $false
    }
    if ($ok) { Write-Pass $resultName }
}
catch {
    Write-Fail -Name $resultName -Endpoint "GET /mock-provider/scenario-runs/$scenarioRunId/result" -Expected "valid scenario result" -Actual $_.Exception.Message
}

$windowEnd = (Get-Date).AddMinutes(2)
$aggregateStart = Get-SmokeTime $windowStart
$aggregateEnd = Get-SmokeTime $windowEnd
$statQueryStart = Get-SmokeHourFloor $windowStart
$statQueryEnd = $aggregateEnd

$aggregateBody = [ordered]@{
    startTime = $aggregateStart
    endTime = $aggregateEnd
    scenarioRunId = $scenarioRunId
    forceRebuild = $true
} | ConvertTo-Json -Depth 5 -Compress

$aggregateName = "stats aggregate"
try {
    $aggregateResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/stats/aggregate" -Body $aggregateBody -TimeoutSec 30
    $ok = Assert-Equal -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "code" -Expected 200 -Actual $aggregateResponse.code
    $ok = (Assert-GreaterThan -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "data.totalLogs" -Threshold 0 -Actual ([int]$aggregateResponse.data.totalLogs)) -and $ok
    $ok = (Assert-GreaterThan -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "data.aggregatedRows" -Threshold 0 -Actual ([int]$aggregateResponse.data.aggregatedRows)) -and $ok
    $itemApiCodes = @($aggregateResponse.data.items | ForEach-Object { $_.apiCode })
    if ($itemApiCodes -notcontains "LECTURE_REGISTER") {
        Write-Fail -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Expected "items contains LECTURE_REGISTER" -Actual ($itemApiCodes -join ",")
        $ok = $false
    }
    $lectureItem = @($aggregateResponse.data.items | Where-Object { $_.apiCode -eq "LECTURE_REGISTER" } | Select-Object -First 1)
    if ($lectureItem.Count -gt 0) {
        $ok = (Assert-GreaterThan -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "LECTURE_REGISTER.totalCount" -Threshold 0 -Actual ([int]$lectureItem[0].totalCount)) -and $ok
        $ok = (Assert-NotNull -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "LECTURE_REGISTER.avgLatencyMs" -Actual $lectureItem[0].avgLatencyMs) -and $ok
        $ok = (Assert-NotNull -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "LECTURE_REGISTER.p95LatencyMs" -Actual $lectureItem[0].p95LatencyMs) -and $ok
        $ok = (Assert-NotNull -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "LECTURE_REGISTER.p99LatencyMs" -Actual $lectureItem[0].p99LatencyMs) -and $ok
        $ok = (Assert-NotNull -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Field "LECTURE_REGISTER.rateLimitCount" -Actual $lectureItem[0].rateLimitCount) -and $ok
    }
    if ($ok) { Write-Pass $aggregateName }
}
catch {
    Write-Fail -Name $aggregateName -Endpoint "POST /api/dev/stats/aggregate" -Expected "aggregated stats response" -Actual $_.Exception.Message
}

$toolBody = [ordered]@{
    apiCode = "LECTURE_REGISTER"
    startTime = $statQueryStart
    endTime = $statQueryEnd
} | ConvertTo-Json -Depth 5 -Compress

$toolName = "queryApiCallStats reads aggregate"
try {
    $toolResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/tools/queryApiCallStats" -Headers @{ "X-Demo-User-Id" = "1" } -Body $toolBody -TimeoutSec 30
    $ok = Assert-Equal -Name $toolName -Endpoint "POST /api/dev/tools/queryApiCallStats" -Field "code" -Expected 200 -Actual $toolResponse.code
    $ok = (Assert-Equal -Name $toolName -Endpoint "POST /api/dev/tools/queryApiCallStats" -Field "data.success" -Expected $true -Actual $toolResponse.data.success) -and $ok
    $ok = (Assert-Equal -Name $toolName -Endpoint "POST /api/dev/tools/queryApiCallStats" -Field "data.toolName" -Expected "queryApiCallStats" -Actual $toolResponse.data.toolName) -and $ok
    $ok = (Assert-GreaterThan -Name $toolName -Endpoint "POST /api/dev/tools/queryApiCallStats" -Field "data.data.totalCallCount" -Threshold 0 -Actual ([int]$toolResponse.data.data.totalCallCount)) -and $ok
    $ok = (Assert-GreaterThan -Name $toolName -Endpoint "POST /api/dev/tools/queryApiCallStats" -Field "data.data.hourlyStats.Count" -Threshold 0 -Actual @($toolResponse.data.data.hourlyStats).Count) -and $ok
    if ($ok) { Write-Pass $toolName }
}
catch {
    Write-Fail -Name $toolName -Endpoint "POST /api/dev/tools/queryApiCallStats" -Expected "ToolResult with totalCallCount > 0" -Actual $_.Exception.Message
}

if ($script:Failed -gt 0) {
    Write-Host "Stats Aggregator smoke failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "[PASS] Scenario Runner -> Gateway Invoke -> gateway_log -> Stats Aggregator -> api_call_stat_hourly -> queryApiCallStats" -ForegroundColor Green
exit 0
