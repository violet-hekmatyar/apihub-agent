param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ScenarioClientBaseUrl = "http://localhost:8090",
    [string]$CampusApiBaseUrl = "http://localhost:8091",
    [switch]$RunLecture,
    [switch]$FastOnly
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$ScenarioClientBaseUrl = $ScenarioClientBaseUrl.TrimEnd("/")
$CampusApiBaseUrl = $CampusApiBaseUrl.TrimEnd("/")
$script:Failed = 0

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [int]$TimeoutSec = 20
    )
    $params = @{ Method = $Method; Uri = $Url; TimeoutSec = $TimeoutSec }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 12 -Compress)
    }
    Invoke-RestMethod @params
}

function Pass($Name) {
    Write-Host "[PASS] $Name" -ForegroundColor Green
}

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
        Write-Host ("  run={0} status={1} elapsed={2}s total={3} fail={4}" -f $RunId,$data.status,$data.elapsedSeconds,$data.totalRequestCount,$data.failCount)
        if ($data.status -in @("COMPLETED","STOPPED","FAILED")) {
            return $data
        }
        Start-Sleep -Seconds 5
    }
    throw "scenario did not finish within $TimeoutSec seconds: $RunId"
}

function Run-OneScenario {
    param([string]$ProfileCode)
    Write-Host "Starting scenario $ProfileCode / FAST_DEMO"
    $body = [ordered]@{
        profileCode = $ProfileCode
        mode = "FAST_DEMO"
        targetGatewayBaseUrl = $BaseUrl
        randomSeed = 20260626
        rpsScale = 1.0
        includeTrafficSamples = $true
    }
    $start = Invoke-Json -Method POST -Url "$ScenarioClientBaseUrl/api/mock/scenario-runs" -Body $body -TimeoutSec 20
    $runId = $start.data.scenarioRunId
    if ([string]::IsNullOrWhiteSpace($runId)) {
        throw "scenarioRunId is empty"
    }
    $timeout = if ($ProfileCode -eq "LECTURE_REGISTRATION_PEAK") { 420 } else { 180 }
    $final = Wait-Scenario -RunId $runId -TimeoutSec $timeout
    if ($final.status -ne "COMPLETED") {
        Fail "$ProfileCode completed" "status=$($final.status)"
    } else {
        Pass "$ProfileCode completed"
    }

    $sender = Invoke-Json -Method GET -Url "$ScenarioClientBaseUrl/api/mock/scenario-runs/$runId/sender-summary" -TimeoutSec 20
    $upstream = Invoke-Json -Method GET -Url "$CampusApiBaseUrl/api/mock-campus/scenario-runs/$runId/upstream-summary" -TimeoutSec 20
    $recon = Invoke-Json -Method GET -Url "$ScenarioClientBaseUrl/api/mock/scenario-runs/$runId/reconciliation-summary" -TimeoutSec 20
    Write-Host "Sender summary:"
    $sender.data | ConvertTo-Json -Depth 10
    Write-Host "Upstream summary:"
    $upstream.data | ConvertTo-Json -Depth 10
    Write-Host "Reconciliation summary:"
    $recon.data | ConvertTo-Json -Depth 10

    if ([int]$sender.data.senderRequestCount -le 0) { Fail "$ProfileCode senderRequestCount" "senderRequestCount=$($sender.data.senderRequestCount)" } else { Pass "$ProfileCode senderRequestCount" }
    if ([int]$upstream.data.upstreamReceivedCount -le 0) { Fail "$ProfileCode upstreamReceivedCount" "upstreamReceivedCount=$($upstream.data.upstreamReceivedCount)" } else { Pass "$ProfileCode upstreamReceivedCount" }
    if ([int]$recon.data.mismatchCount -ne 0) { Fail "$ProfileCode reconciliation" "mismatchCount=$($recon.data.mismatchCount)" } else { Pass "$ProfileCode reconciliation" }
}

Write-Host "Mock Scenario Runner smoke target: 8080=$BaseUrl 8090=$ScenarioClientBaseUrl 8091=$CampusApiBaseUrl"

try { Invoke-Json -Method GET -Url "$BaseUrl/api/health" -TimeoutSec 10 | Out-Null; Pass "8080 health" } catch { Fail "8080 health" $_.Exception.Message }
try { Invoke-Json -Method GET -Url "$ScenarioClientBaseUrl/api/mock/health" -TimeoutSec 10 | Out-Null; Pass "8090 scenario client health" } catch { Fail "8090 scenario client health" $_.Exception.Message }
try { Invoke-Json -Method GET -Url "$CampusApiBaseUrl/api/mock-campus/health" -TimeoutSec 10 | Out-Null; Pass "8091 campus api health" } catch { Fail "8091 campus api health" $_.Exception.Message }
if ($script:Failed -gt 0) { exit 1 }

$profiles = Invoke-Json -Method GET -Url "$ScenarioClientBaseUrl/api/mock/scenario-profiles" -TimeoutSec 10
if (@($profiles.data).Count -lt 4) { Fail "scenario profiles" "count=$(@($profiles.data).Count)" } else { Pass "scenario profiles" }
if ($script:Failed -gt 0) { exit 1 }

Run-OneScenario -ProfileCode "NORMAL_DAILY_INSPECTION"
if ($RunLecture) {
    Run-OneScenario -ProfileCode "LECTURE_REGISTRATION_PEAK"
}

if ($script:Failed -gt 0) {
    Write-Host "Mock Scenario Runner smoke failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "[PASS] Mock Scenario Runner smoke completed" -ForegroundColor Green
