param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$MockProviderBaseUrl = "http://localhost:8090",
    [int]$DurationSeconds = 60,
    [int]$IntervalMs = 1000,
    [switch]$SkipPdf
)

$ErrorActionPreference = "Stop"
$script:Failed = 0
$BaseUrl = $BaseUrl.TrimEnd("/")
$MockProviderBaseUrl = $MockProviderBaseUrl.TrimEnd("/")
$RunStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$RootDir = "D:\tmp\apihub-agent-normal-baseline-test"
$RunDir = Join-Path $RootDir "output\run-$RunStamp"
$RawDir = Join-Path $RunDir "raw"
$LogDir = Join-Path $RunDir "logs"
$HtmlDir = Join-Path $RunDir "html"
$PdfDir = Join-Path $RunDir "pdf"
$SummaryDir = Join-Path $RunDir "summary"
New-Item -ItemType Directory -Force -Path $RawDir, $LogDir, $HtmlDir, $PdfDir, $SummaryDir | Out-Null

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
        [int]$TimeoutSec = 20
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

function Save-Json {
    param([string]$Path, $Value)
    $Value | ConvertTo-Json -Depth 30 | Out-File -FilePath $Path -Encoding utf8
}

function Write-Pass {
    param([string]$Name)
    Write-Host "[PASS] $Name" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Name, [string]$Expected, $Actual)
    $script:Failed++
    Write-Host "[FAIL] $Name" -ForegroundColor Red
    Write-Host "       expected: $Expected"
    Write-Host "       actual:   $Actual"
}

function Assert-Equal {
    param([string]$Name, [string]$Field, $Expected, $Actual)
    if ($Expected -ne $Actual) {
        Write-Fail -Name $Name -Expected "$Field=$Expected" -Actual "$Field=$Actual"
        return $false
    }
    return $true
}

function Assert-GreaterThan {
    param([string]$Name, [string]$Field, [int]$Threshold, [int]$Actual)
    if ($Actual -le $Threshold) {
        Write-Fail -Name $Name -Expected "$Field>$Threshold" -Actual "$Field=$Actual"
        return $false
    }
    return $true
}

function Get-SmokeTime {
    param([datetime]$Value)
    return $Value.ToString("yyyy-MM-dd HH:mm:ss")
}

function Find-Browser {
    $candidates = @(
        "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files\Google\Chrome\Application\chrome.exe",
        "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }
    return $null
}

function Export-ReportPdf {
    param([string]$HtmlPath, [string]$PdfPath)
    if ($SkipPdf) {
        return [ordered]@{ success = $false; skipped = $true; message = "SkipPdf was set." }
    }
    $browser = Find-Browser
    if (-not $browser) {
        return [ordered]@{ success = $false; skipped = $false; message = "No Edge or Chrome executable was found." }
    }
    $htmlUri = (New-Object System.Uri($HtmlPath)).AbsoluteUri
    foreach ($args in @(
        @("--headless=new", "--disable-gpu", "--print-to-pdf=$PdfPath", $htmlUri),
        @("--headless", "--disable-gpu", "--print-to-pdf=$PdfPath", $htmlUri)
    )) {
        if (Test-Path $PdfPath) { Remove-Item -LiteralPath $PdfPath -Force }
        $process = Start-Process -FilePath $browser -ArgumentList $args -Wait -PassThru -WindowStyle Hidden
        Start-Sleep -Seconds 2
        if ((Test-Path $PdfPath) -and ((Get-Item $PdfPath).Length -gt 10240)) {
            return [ordered]@{ success = $true; browser = $browser; exitCode = $process.ExitCode; pdfPath = $PdfPath; sizeBytes = (Get-Item $PdfPath).Length }
        }
    }
    return [ordered]@{ success = $false; skipped = $false; browser = $browser; message = "Browser headless print did not create a PDF larger than 10KB." }
}

function Send-NormalBaselineTraffic {
    param([string]$ScenarioRunId)
    $apis = @("AUTH_LOGIN", "COURSE_TODAY", "LECTURE_LIST", "CAMPUS_NOTICE", "LIBRARY_BORROW")
    $logPath = Join-Path $LogDir "normal-baseline-gateway-traffic.jsonl"
    $deadline = (Get-Date).AddSeconds($DurationSeconds)
    $sequence = 1
    while ((Get-Date) -lt $deadline) {
        $apiCode = $apis[($sequence - 1) % $apis.Count]
        $body = [ordered]@{
            apiCode = $apiCode
            mockScenario = "NORMAL"
            timeoutMs = 3000
            clientInfo = [ordered]@{
                clientIp = "10.30.10.$(($sequence % 200) + 1)"
                userAgent = "apihub-normal-baseline-control/1.0"
            }
            scenarioContext = [ordered]@{
                scenarioRunId = $ScenarioRunId
                scenarioId = "NORMAL_BASELINE_CONTROL"
                scenarioKey = "LOW_TRAFFIC_CONTROL"
                phase = "NORMAL_BASELINE"
                sequenceNo = $sequence
            }
            queryParams = [ordered]@{
                studentId = "normal_stu_$sequence"
                noticeId = "normal_notice_$sequence"
                bookId = "normal_book_$sequence"
            }
            body = [ordered]@{
                requestNo = "normal-baseline-$sequence"
            }
        } | ConvertTo-Json -Depth 8 -Compress
        $started = Get-Date
        try {
            $response = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/gateway/invoke" -Headers @{ "X-Request-Id" = "normal-baseline-$sequence" } -Body $body -TimeoutSec 10
            $record = [ordered]@{
                time = Get-SmokeTime (Get-Date)
                apiCode = $apiCode
                code = $response.code
                success = $response.data.success
                upstreamStatus = $response.data.upstreamStatus
                latencyMs = $response.data.latencyMs
                traceId = $response.data.traceId
                gatewayLogId = $response.data.gatewayLogId
            }
        }
        catch {
            $record = [ordered]@{
                time = Get-SmokeTime (Get-Date)
                apiCode = $apiCode
                code = $null
                success = $false
                error = $_.Exception.Message
                elapsedMs = [int]((Get-Date) - $started).TotalMilliseconds
            }
        }
        ($record | ConvertTo-Json -Depth 8 -Compress) | Add-Content -Path $logPath -Encoding utf8
        $sequence++
        Start-Sleep -Milliseconds $IntervalMs
    }
    return [ordered]@{ sent = $sequence - 1; apiCodes = $apis; logPath = $logPath }
}

Write-Host "Normal Baseline Control smoke target: server=$BaseUrl mock-provider=$MockProviderBaseUrl"
$overallStart = Get-Date
$scenarioRunId = "normal_baseline_$RunStamp"
$diagnosisApiCode = "COURSE_TODAY"
$testStart = Get-Date
$testEnd = $null
$reportId = $null
$riskLevel = $null
$htmlPath = $null
$pdfPath = $null
$alertCount = $null

try {
    Save-Json (Join-Path $RawDir "health-server.json") (Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/health")
    Save-Json (Join-Path $RawDir "health-mock-provider.json") (Invoke-JsonRequest -Method "GET" -Url "$MockProviderBaseUrl/mock-provider/health")
    Write-Pass "health checks"

    $traffic = Send-NormalBaselineTraffic -ScenarioRunId $scenarioRunId
    $testEnd = Get-Date
    if ($traffic.sent -lt 20) { throw "normal baseline sent too few gateway requests: $($traffic.sent)" }
    Write-Pass "normal baseline traffic sent $($traffic.sent) gateway requests"

    $windowStart = Get-SmokeTime $testStart.AddSeconds(-10)
    $windowEnd = Get-SmokeTime $testEnd.AddSeconds(10)

    $statsBody = [ordered]@{
        startTime = $windowStart
        endTime = $windowEnd
        scenarioRunId = $scenarioRunId
        apiCode = $diagnosisApiCode
        forceRebuild = $true
    } | ConvertTo-Json -Depth 8 -Compress
    $statsResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/stats/aggregate" -Body $statsBody -TimeoutSec 60
    Save-Json (Join-Path $RawDir "stats-aggregate-response.json") $statsResponse
    $ok = Assert-Equal -Name "stats aggregate" -Field "code" -Expected 200 -Actual $statsResponse.code
    $ok = (Assert-GreaterThan -Name "stats aggregate" -Field "data.aggregatedRows" -Threshold 0 -Actual ([int]$statsResponse.data.aggregatedRows)) -and $ok
    if ($ok) { Write-Pass "stats aggregate" }

    $alertBody = [ordered]@{
        startTime = $windowStart
        endTime = $windowEnd
        apiCode = $diagnosisApiCode
        scenarioRunId = $scenarioRunId
        mode = "DEV_SHORT_WINDOW"
        windowSeconds = 30
        forceRebuild = $true
    } | ConvertTo-Json -Depth 8 -Compress
    $alertResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/alerts/evaluate" -Body $alertBody -TimeoutSec 60
    Save-Json (Join-Path $RawDir "alert-evaluate-response.json") $alertResponse
    $alertCount = [int]$alertResponse.data.createdAlertCount + [int]$alertResponse.data.updatedAlertCount
    $ok = Assert-Equal -Name "alert evaluate normal baseline" -Field "code" -Expected 200 -Actual $alertResponse.code
    $ok = (Assert-Equal -Name "alert evaluate normal baseline" -Field "created+updated alerts" -Expected 0 -Actual $alertCount) -and $ok
    if ($ok) { Write-Pass "alert evaluate produced no alerts" }

    $diagnosisBody = [ordered]@{
        apiCode = $diagnosisApiCode
        startTime = $windowStart
        endTime = $windowEnd
        scenarioRunId = $scenarioRunId
        diagnosisMode = "DETERMINISTIC"
        forceRebuild = $true
    } | ConvertTo-Json -Depth 8 -Compress
    $diagnosisResponse = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/agent/diagnose" -Headers @{ "X-Demo-User-Id" = "1" } -Body $diagnosisBody -TimeoutSec 60
    Save-Json (Join-Path $RawDir "agent-diagnose-response.json") $diagnosisResponse
    $reportId = $diagnosisResponse.data.reportId
    $riskLevel = $diagnosisResponse.data.riskLevel
    $ok = Assert-Equal -Name "agent diagnosis normal baseline" -Field "code" -Expected 200 -Actual $diagnosisResponse.code
    $ok = (Assert-Equal -Name "agent diagnosis normal baseline" -Field "riskLevel" -Expected "NORMAL" -Actual $riskLevel) -and $ok
    if ($ok) { Write-Pass "agent diagnosis riskLevel=NORMAL reportId=$reportId" }

    $reportDetail = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/dev/agent/reports/$reportId" -TimeoutSec 30
    Save-Json (Join-Path $RawDir "report-detail-response.json") $reportDetail
    if (@($reportDetail.data.evidenceItems).Count -lt 3) { throw "report evidenceItems < 3" }
    if (@($reportDetail.data.toolCallTraces).Count -lt 3) { throw "report toolCallTraces < 3" }
    Write-Pass "report detail"

    $htmlRaw = (curl.exe -s --max-time 30 "$BaseUrl/api/dev/agent/reports/$reportId/html") -join "`r`n"
    $htmlPath = Join-Path $HtmlDir "apihub-agent-normal-baseline-report-$reportId.html"
    $htmlRaw | Out-File -FilePath $htmlPath -Encoding utf8
    if ($htmlRaw -notlike "*API-HUB Agent*" -or $htmlRaw -notlike "*$diagnosisApiCode*") {
        throw "HTML report validation failed"
    }
    Write-Pass "HTML report saved: $htmlPath"

    $pdfPath = Join-Path $PdfDir "apihub-agent-normal-baseline-report-$reportId.pdf"
    $pdfResult = Export-ReportPdf -HtmlPath $htmlPath -PdfPath $pdfPath
    Save-Json (Join-Path $RawDir "pdf-export-result.json") $pdfResult
    if ($pdfResult.success) {
        Write-Pass "PDF exported: $pdfPath"
    } else {
        Write-Host "[WARN] PDF export skipped/failed: $($pdfResult.message)" -ForegroundColor Yellow
    }

    $summaryPath = Join-Path $SummaryDir "normal-baseline-summary.md"
    $passed = ($script:Failed -eq 0)
    @"
# API-HUB Agent normal baseline control smoke

1. Test name: Normal baseline control
2. Test window: $windowStart ~ $windowEnd
3. scenarioRunId: $scenarioRunId
4. Diagnosis API: $diagnosisApiCode
5. Traffic model: ${DurationSeconds}s low-traffic NORMAL Gateway Invoke calls, intervalMs=$IntervalMs
6. APIs: $($traffic.apiCodes -join ", ")
7. Gateway requests sent: $($traffic.sent)
8. Stats Aggregator: aggregatedRows=$($statsResponse.data.aggregatedRows), totalLogs=$($statsResponse.data.totalLogs)
9. Alert Evaluator: alertCount=$alertCount
10. Agent Diagnosis: status=$($diagnosisResponse.data.status), riskLevel=$riskLevel
11. reportId: $reportId
12. HTML report path: $htmlPath
13. PDF report path: $pdfPath
14. Raw JSON path: $RawDir
15. Test passed: $passed

Output directory: $RunDir
"@ | Out-File -FilePath $summaryPath -Encoding utf8
    Write-Pass "summary saved: $summaryPath"

    if ($script:Failed -gt 0) { exit 2 }
    exit 0
}
catch {
    Write-Fail -Name "normal baseline control smoke" -Expected "completed normal baseline control chain" -Actual $_.Exception.Message
    $summaryPath = Join-Path $SummaryDir "normal-baseline-summary.md"
    @"
# API-HUB Agent normal baseline control smoke

Test failed.

- Error: $($_.Exception.Message)
- scenarioRunId: $scenarioRunId
- reportId: $reportId
- riskLevel: $riskLevel
- alertCount: $alertCount
- Output directory: $RunDir
- Raw JSON path: $RawDir
- HTML path: $htmlPath
- PDF path: $pdfPath
"@ | Out-File -FilePath $summaryPath -Encoding utf8
    exit 1
}
finally {
    $elapsed = [int]((Get-Date) - $overallStart).TotalSeconds
    Write-Host "Normal baseline control elapsed seconds: $elapsed"
    Write-Host "Output directory: $RunDir"
}
