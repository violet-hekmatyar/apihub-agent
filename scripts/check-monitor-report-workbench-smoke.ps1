param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [switch]$IncludeLlm,
    [string]$MonitorEventId,
    [string]$Range = "24h",
    [switch]$ExportPdf,
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body
    )

    $headers = @{
        "Content-Type" = "application/json"
        "X-Request-Id" = "monitor-report-workbench-smoke-" + [guid]::NewGuid().ToString("N")
    }

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -TimeoutSec 30
    }
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body ($Body | ConvertTo-Json -Depth 20) -TimeoutSec 60
}

function Assert-SuccessResponse {
    param([object]$Response, [string]$Name)

    if ($null -eq $Response) {
        throw "$Name returned empty response."
    }
    if ($Response.code -and $Response.code -ne 200) {
        throw "$Name failed: code=$($Response.code), message=$($Response.message)"
    }
}

$health = Invoke-Json -Method GET -Url "$BaseUrl/api/health" -Body $null
Assert-SuccessResponse -Response $health -Name "health"

$body = @{
    includeLlm = [bool]$IncludeLlm
    includePrompt = $false
}

if ($MonitorEventId) {
    $body.monitorEventId = $MonitorEventId
    $result = Invoke-Json -Method POST -Url "$BaseUrl/api/dev/report-workbench/from-monitor-event" -Body $body
} else {
    $recentEvents = Invoke-Json -Method GET -Url "$BaseUrl/api/dev/passive-monitor/events/recent?limit=1" -Body $null
    $eventId = $null
    if ($recentEvents.data -and $recentEvents.data.Count -gt 0) {
        $eventId = $recentEvents.data[0].monitorEventId
        if (-not $eventId) {
            $eventId = $recentEvents.data[0].monitor_event_id
        }
    }

    if ($eventId) {
        $body.monitorEventId = $eventId
        $result = Invoke-Json -Method POST -Url "$BaseUrl/api/dev/report-workbench/from-monitor-event" -Body $body
    } else {
        $rangeBody = @{
            range = $Range
            includeLlm = [bool]$IncludeLlm
            includePrompt = $false
            includeNormalSummary = $true
        }
        $result = Invoke-Json -Method POST -Url "$BaseUrl/api/dev/report-workbench/analyze-range" -Body $rangeBody
    }
}

Assert-SuccessResponse -Response $result -Name "workbench"
$data = $result.data
if (-not $data.reportId) {
    throw "workbench did not return reportId. status=$($data.status), message=$($data.message)"
}

$detail = Invoke-Json -Method GET -Url "$BaseUrl/api/dev/report-workbench/reports/$($data.reportId)" -Body $null
Assert-SuccessResponse -Response $detail -Name "report detail"

$htmlUrl = "$BaseUrl$($data.htmlUrl)"
$html = Invoke-WebRequest -Method GET -Uri $htmlUrl -UseBasicParsing -TimeoutSec 30
if ($html.StatusCode -ne 200 -or -not $html.Content.Contains("API-HUB Monitor Report Workbench")) {
    throw "report html check failed: status=$($html.StatusCode)"
}

$pdfPath = $null
if ($ExportPdf) {
    if (-not $OutputPath) {
        $OutputPath = Join-Path $env:TEMP ("apihub-monitor-report-" + $data.reportId + ".pdf")
    }
    & (Join-Path $PSScriptRoot "export-monitor-report-pdf.ps1") -ReportHtmlUrl $htmlUrl -OutputPath $OutputPath | Out-Host
    $pdfPath = [System.IO.Path]::GetFullPath($OutputPath)
}

[pscustomobject]@{
    reportId = $data.reportId
    reportType = $data.reportType
    htmlUrl = $htmlUrl
    llmStatus = $data.llmStatus
    displayStatus = $data.displayStatus
    pdfPath = $pdfPath
} | Format-List
