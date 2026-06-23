param(
    [string]$BaseUrl = "http://localhost:8080",
    [int64]$ReportId = 0,
    [switch]$IncludePrompt
)

$ErrorActionPreference = "Stop"
$script:Failed = 0
$BaseUrl = $BaseUrl.TrimEnd("/")

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
        [string]$Body = $null,
        [int]$TimeoutSec = 15
    )
    $params = @{ Uri = $Url; Method = $Method; TimeoutSec = $TimeoutSec }
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

function Assert-NotNull {
    param($Name, $Endpoint, $Field, $Actual)
    if ($null -eq $Actual -or ([string]$Actual).Length -eq 0) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field exists" -Actual "$Field=<empty>"
        return $false
    }
    return $true
}

function Assert-True {
    param($Name, $Endpoint, $Field, $Actual)
    if ($true -ne $Actual) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field=true" -Actual "$Field=$Actual"
        return $false
    }
    return $true
}

function Assert-False {
    param($Name, $Endpoint, $Field, $Actual)
    if ($false -ne $Actual) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field=false" -Actual "$Field=$Actual"
        return $false
    }
    return $true
}

Write-Host "LLM Prompt Builder mock smoke target: server=$BaseUrl"

try {
    $response = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/health" -TimeoutSec 10
    $ok = Assert-Equal -Name "apihub-server health" -Endpoint "GET /api/health" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-Equal -Name "apihub-server health" -Endpoint "GET /api/health" -Field "data.status" -Expected "UP" -Actual $response.data.status) -and $ok
    if ($ok) { Write-Pass "apihub-server health" }
}
catch {
    Write-Fail -Name "apihub-server health" -Endpoint "GET /api/health" -Expected "reachable JSON response" -Actual $_.Exception.Message
}

if ($script:Failed -gt 0) { exit 1 }

if ($ReportId -le 0) {
    try {
        $response = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/dev/agent/reports?pageNo=1&pageSize=1" -TimeoutSec 15
        $ok = Assert-Equal -Name "report lookup" -Endpoint "GET /api/dev/agent/reports" -Field "code" -Expected 200 -Actual $response.code
        if ($ok -and $response.data.total -gt 0 -and @($response.data.items).Count -gt 0) {
            $ReportId = [int64]$response.data.items[0].reportId
            Write-Pass "report lookup reportId=$ReportId"
        }
        else {
            Write-Fail -Name "report lookup" -Endpoint "GET /api/dev/agent/reports" -Expected "at least one diagnosis report" -Actual "total=$($response.data.total)"
        }
    }
    catch {
        Write-Fail -Name "report lookup" -Endpoint "GET /api/dev/agent/reports" -Expected "latest report" -Actual $_.Exception.Message
    }
}

if ($script:Failed -gt 0) { exit 1 }

$body = [ordered]@{
    reportId = $ReportId
    includePrompt = [bool]$IncludePrompt
} | ConvertTo-Json -Depth 6 -Compress

try {
    $response = Invoke-JsonRequest -Method "POST" -Url "$BaseUrl/api/dev/agent/diagnose/llm/mock" -Body $body -TimeoutSec 20
    $data = $response.data
    $ok = Assert-Equal -Name "llm mock diagnose" -Endpoint "POST /api/dev/agent/diagnose/llm/mock" -Field "code" -Expected 200 -Actual $response.code
    $ok = (Assert-True -Name "llm mock diagnose" -Endpoint "POST /api/dev/agent/diagnose/llm/mock" -Field "data.success" -Actual $data.success) -and $ok
    $ok = (Assert-False -Name "llm mock diagnose" -Endpoint "POST /api/dev/agent/diagnose/llm/mock" -Field "data.fallbackUsed" -Actual $data.fallbackUsed) -and $ok
    $ok = (Assert-True -Name "llm mock diagnose" -Endpoint "POST /api/dev/agent/diagnose/llm/mock" -Field "data.validation.success" -Actual $data.validation.success) -and $ok
    $ok = (Assert-NotNull -Name "llm mock diagnose" -Endpoint "POST /api/dev/agent/diagnose/llm/mock" -Field "data.output.riskLevel" -Actual $data.output.riskLevel) -and $ok
    $ok = (Assert-NotNull -Name "llm mock diagnose" -Endpoint "POST /api/dev/agent/diagnose/llm/mock" -Field "data.rawResponse" -Actual $data.rawResponse) -and $ok
    if ($IncludePrompt) {
        $ok = (Assert-NotNull -Name "llm mock diagnose" -Endpoint "POST /api/dev/agent/diagnose/llm/mock" -Field "data.prompt.inputJson" -Actual $data.prompt.inputJson) -and $ok
    }
    if ($ok) { Write-Pass "llm mock diagnose reportId=$ReportId riskLevel=$($data.output.riskLevel)" }
}
catch {
    Write-Fail -Name "llm mock diagnose" -Endpoint "POST /api/dev/agent/diagnose/llm/mock" -Expected "validated mock result" -Actual $_.Exception.Message
}

if ($script:Failed -gt 0) {
    Write-Host "LLM Prompt Builder mock smoke failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "[PASS] LLM Prompt Builder mock smoke completed" -ForegroundColor Green
exit 0
