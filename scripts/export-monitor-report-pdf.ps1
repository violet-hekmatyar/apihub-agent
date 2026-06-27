param(
    [Parameter(Mandatory = $true)]
    [string]$ReportHtmlUrl,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [string]$EdgePath,

    [int]$WaitSeconds = 3
)

$ErrorActionPreference = "Stop"

function Resolve-BrowserPath {
    param([string]$CustomPath)

    if ($CustomPath -and (Test-Path -LiteralPath $CustomPath)) {
        return (Resolve-Path -LiteralPath $CustomPath).Path
    }

    $candidates = @(
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe",
        "${env:LocalAppData}\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles}\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "${env:LocalAppData}\Google\Chrome\Application\chrome.exe"
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }

    throw "Edge/Chrome executable was not found. Pass -EdgePath with a valid browser path."
}

$browser = Resolve-BrowserPath -CustomPath $EdgePath
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$outputDir = Split-Path -Parent $resolvedOutput
if ($outputDir -and -not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$userDataDir = Join-Path $env:TEMP ("apihub-monitor-report-pdf-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $userDataDir | Out-Null

try {
    $args = @(
        "--headless=new",
        "--disable-gpu",
        "--no-first-run",
        "--disable-extensions",
        "--user-data-dir=$userDataDir",
        "--virtual-time-budget=$([Math]::Max(1, $WaitSeconds) * 1000)",
        "--print-to-pdf=$resolvedOutput",
        $ReportHtmlUrl
    )

    $process = Start-Process -FilePath $browser -ArgumentList $args -PassThru -WindowStyle Hidden
    if (-not $process.WaitForExit([Math]::Max(10, $WaitSeconds + 15) * 1000)) {
        $process.Kill()
        throw "Browser print timed out."
    }
    if ($process.ExitCode -ne 0) {
        throw "Browser print failed with exit code $($process.ExitCode)."
    }
    if (-not (Test-Path -LiteralPath $resolvedOutput)) {
        throw "PDF was not created: $resolvedOutput"
    }

    Write-Host "pdfPath=$resolvedOutput"
} finally {
    Remove-Item -LiteralPath $userDataDir -Recurse -Force -ErrorAction SilentlyContinue
}
