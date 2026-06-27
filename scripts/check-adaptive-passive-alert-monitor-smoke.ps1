param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ScenarioClientBaseUrl = "http://localhost:8090",
    [string]$CampusApiBaseUrl = "http://localhost:8091",
    [switch]$RunLecture,
    [switch]$RunAuth,
    [switch]$RunTimeout,
    [switch]$SkipWaitClose,
    [switch]$RunCloseCheck
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$ScenarioClientBaseUrl = $ScenarioClientBaseUrl.TrimEnd("/")
$CampusApiBaseUrl = $CampusApiBaseUrl.TrimEnd("/")
$script:Failed = 0

function Invoke-Json {
    param([string]$Method, [string]$Url, [object]$Body = $null, [int]$TimeoutSec = 20)
    $params = @{ Method = $Method; Uri = $Url; TimeoutSec = $TimeoutSec }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 12 -Compress)
    }
    Invoke-RestMethod @params
}

function Pass($Name) { Write-Host "[PASS] $Name" -ForegroundColor Green }
function Fail($Name, $Actual) {
    $script:Failed++
    Write-Host "[FAIL] $Name" -ForegroundColor Red
    Write-Host "       $Actual"
}

function Wait-Scenario {
    param([string]$RunId, [int]$TimeoutSec)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $status = Invoke-Json -Method GET -Url "$ScenarioClientBaseUrl/api/mock/scenario-runs/$RunId" -TimeoutSec 10
        $data = $status.data
        Write-Host ("  scenario={0} status={1} elapsed={2}s total={3} fail={4}" -f $RunId,$data.status,$data.elapsedSeconds,$data.totalRequestCount,$data.failCount)
        if ($data.status -in @("COMPLETED","STOPPED","FAILED")) { return $data }
        Start-Sleep -Seconds 5
    }
    throw "scenario did not finish within $TimeoutSec seconds: $RunId"
}

function Find-RecentAlert {
    param([string[]]$AlertTypes, [int]$TimeoutSec = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $events = Invoke-Json -Method GET -Url "$BaseUrl/api/dev/passive-monitor/events/recent?limit=50" -TimeoutSec 10
        foreach ($event in @($events.data)) {
            if ($AlertTypes -contains [string]$event.alert_type) {
                return $event
            }
        }
        Start-Sleep -Seconds 5
    }
    return $null
}

function Run-ScenarioAndAssertAlert {
    param([string]$ProfileCode, [string[]]$AlertTypes)
    Write-Host "Starting $ProfileCode / FAST_DEMO"
    $body = [ordered]@{
        profileCode = $ProfileCode
        mode = "FAST_DEMO"
        targetGatewayBaseUrl = $BaseUrl
        randomSeed = 20260627
        rpsScale = 1.0
        includeTrafficSamples = $true
    }
    $started = Invoke-Json -Method POST -Url "$ScenarioClientBaseUrl/api/mock/scenario-runs" -Body $body -TimeoutSec 20
    $runId = $started.data.scenarioRunId
    if ([string]::IsNullOrWhiteSpace($runId)) { throw "scenarioRunId is empty" }
    Write-Host "  runId=$runId"

    $alert = Find-RecentAlert -AlertTypes $AlertTypes -TimeoutSec 120
    if ($null -eq $alert) {
        Fail "$ProfileCode alert detected" "Expected one of: $($AlertTypes -join ', ')"
    } else {
        Pass "$ProfileCode alert detected: $($alert.alert_type)"
        Write-Host ("  monitorEventId={0} first={1} last={2} status={3} type={4} api={5}" -f $alert.monitor_event_id,$alert.first_trigger_time,$alert.last_trigger_time,$alert.event_status,$alert.alert_type,$alert.api_code)
        $detail = Invoke-Json -Method GET -Url "$BaseUrl/api/dev/passive-monitor/events/$($alert.monitor_event_id)" -TimeoutSec 10
        if (@($detail.data.snapshots).Count -gt 0) { Pass "$ProfileCode event detail snapshots" } else { Fail "$ProfileCode event detail snapshots" "no snapshots" }
    }

    if (-not $SkipWaitClose) {
        $timeout = if ($ProfileCode -eq "LECTURE_REGISTRATION_PEAK") { 420 } else { 260 }
        $final = Wait-Scenario -RunId $runId -TimeoutSec $timeout
        if ($final.status -eq "COMPLETED") { Pass "$ProfileCode completed" } else { Fail "$ProfileCode completed" "status=$($final.status)" }
    }
}

Write-Host "Adaptive Passive Alert Monitor smoke target: 8080=$BaseUrl 8090=$ScenarioClientBaseUrl 8091=$CampusApiBaseUrl"

try { Invoke-Json -Method GET -Url "$BaseUrl/api/health" -TimeoutSec 10 | Out-Null; Pass "8080 health" } catch { Fail "8080 health" $_.Exception.Message }
try { Invoke-Json -Method GET -Url "$ScenarioClientBaseUrl/api/mock/health" -TimeoutSec 10 | Out-Null; Pass "8090 scenario client health" } catch { Fail "8090 scenario client health" $_.Exception.Message }
try { Invoke-Json -Method GET -Url "$CampusApiBaseUrl/api/mock-campus/health" -TimeoutSec 10 | Out-Null; Pass "8091 campus api health" } catch { Fail "8091 campus api health" $_.Exception.Message }
if ($script:Failed -gt 0) { exit 1 }

$status = Invoke-Json -Method GET -Url "$BaseUrl/api/dev/passive-monitor/status" -TimeoutSec 10
if ($null -ne $status.data) { Pass "passive monitor status" } else { Fail "passive monitor status" "empty data" }

$config = [ordered]@{
    enabled = $true
    bucketSeconds = 5
    shortWindowSeconds = 30
    baselineWindowSeconds = 300
    contextBeforeSeconds = 60
    cooldownSeconds = 120
    minRequestCount = 20
    minErrorCount = 3
    highErrorRateThreshold = 0.10
    highRateLimitThreshold = 0.05
    high5xxRateThreshold = 0.05
    authFailureThreshold = 0.10
    latencyThresholdMs = 1000
}
Invoke-Json -Method POST -Url "$BaseUrl/api/dev/passive-monitor/config" -Body $config -TimeoutSec 10 | Out-Null
$startedMonitor = Invoke-Json -Method POST -Url "$BaseUrl/api/dev/passive-monitor/start" -TimeoutSec 10
if ($startedMonitor.data.enabled -eq $true) { Pass "passive monitor start" } else { Fail "passive monitor start" ($startedMonitor | ConvertTo-Json -Depth 8) }

Invoke-Json -Method GET -Url "$BaseUrl/api/dev/passive-monitor/events/recent?limit=5" -TimeoutSec 10 | Out-Null
Pass "passive monitor recent events query"

if ($RunLecture) {
    Run-ScenarioAndAssertAlert -ProfileCode "LECTURE_REGISTRATION_PEAK" -AlertTypes @("HIGH_RATE_LIMIT","HIGH_ERROR_RATE","TRAFFIC_SPIKE")
}
if ($RunAuth) {
    Run-ScenarioAndAssertAlert -ProfileCode "AUTH_FAILURE_LOCALIZED" -AlertTypes @("AUTH_FAILURE_SPIKE","HIGH_ERROR_RATE")
}
if ($RunTimeout) {
    Run-ScenarioAndAssertAlert -ProfileCode "DOWNSTREAM_TIMEOUT_DEGRADATION" -AlertTypes @("HIGH_5XX_RATE","HIGH_ERROR_RATE","HIGH_LATENCY")
}

if ($RunCloseCheck) {
    $closed = Invoke-Json -Method POST -Url "$BaseUrl/api/dev/passive-monitor/events/close-check" -TimeoutSec 20
    Write-Host "Close check:"
    $closed.data | ConvertTo-Json -Depth 8
    Pass "passive monitor close-check"
}

Invoke-Json -Method POST -Url "$BaseUrl/api/dev/passive-monitor/stop" -TimeoutSec 10 | Out-Null
Pass "passive monitor stop"

if ($script:Failed -gt 0) {
    Write-Host "Adaptive Passive Alert Monitor smoke failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "[PASS] Adaptive Passive Alert Monitor smoke completed" -ForegroundColor Green
