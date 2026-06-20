param(
    [string]$BaseUrl = "http://localhost:8090"
)

$ErrorActionPreference = "Stop"
$script:Failed = 0
$BaseUrl = $BaseUrl.TrimEnd("/")

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

function Invoke-MockRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [hashtable]$Headers = @{},

        [string]$Body = $null
    )

    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $Headers
        TimeoutSec = 10
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

function Assert-ContainsText {
    param($Name, $Endpoint, $Field, $ExpectedText, $Actual)

    if ([string]$Actual -notlike "*$ExpectedText*") {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field contains $ExpectedText" -Actual "$Field=$Actual"
        return $false
    }
    return $true
}

function Run-MockSmoke {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [int]$ExpectedCode,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [scriptblock]$ExtraChecks = $null
    )

    $endpoint = "$Method $Path"
    try {
        $response = Invoke-MockRequest -Method $Method -Path $Path -Headers $Headers -Body $Body
    }
    catch {
        Write-Fail -Name $Name -Endpoint $endpoint -Expected "reachable JSON response" -Actual $_.Exception.Message
        return
    }

    $ok = Assert-Equal -Name $Name -Endpoint $endpoint -Field "code" -Expected $ExpectedCode -Actual $response.code
    if ($ok -and $null -ne $ExtraChecks) {
        $extraOk = & $ExtraChecks $response $Name $endpoint
        if ($false -eq $extraOk) {
            $ok = $false
        }
    }

    if ($ok) {
        Write-Host "[PASS] $Name" -ForegroundColor Green
    }
}

Write-Host "Mock provider smoke test target: $BaseUrl"

Run-MockSmoke -Name "health" -Method "GET" -Path "/mock-provider/health" -ExpectedCode 200 -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-Equal -Name $name -Endpoint $endpoint -Field "data.status" -Expected "UP" -Actual $response.data.status
}

Run-MockSmoke -Name "auth login normal" -Method "POST" -Path "/mock-provider/auth/login" -ExpectedCode 200 -Body '{"appCode":"COURSE_HELPER","studentNo":"2023001001","timestamp":"2026-06-19T12:00:00","nonce":"mock_nonce_001","signature":"mock_signature_valid"}' -ExtraChecks {
    param($response, $name, $endpoint)
    $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.tokenType" -Expected "Bearer" -Actual $response.data.tokenType
    $ok = (Assert-ContainsText -Name $name -Endpoint $endpoint -Field "data.accessToken" -ExpectedText "mock_token" -Actual $response.data.accessToken) -and $ok
    return $ok
}

Run-MockSmoke -Name "auth login signature mismatch" -Method "POST" -Path "/mock-provider/auth/login" -Headers @{ "X-Mock-Scenario" = "SIGNATURE_MISMATCH" } -ExpectedCode 403 -Body '{"appCode":"COURSE_HELPER","studentNo":"2023001001"}' -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-ContainsText -Name $name -Endpoint $endpoint -Field "message" -ExpectedText "signature mismatch" -Actual $response.message
}

Run-MockSmoke -Name "course today normal" -Method "GET" -Path "/mock-provider/course/today?studentNo=2023001001&date=2026-06-19" -ExpectedCode 200 -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.courses.Count" -Threshold 0 -Actual @($response.data.courses).Count
}

Run-MockSmoke -Name "lecture list normal" -Method "GET" -Path "/mock-provider/lecture/list?date=2026-06-19" -ExpectedCode 200 -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.lectures.Count" -Threshold 0 -Actual @($response.data.lectures).Count
}

Run-MockSmoke -Name "lecture register normal" -Method "POST" -Path "/mock-provider/lecture/register" -ExpectedCode 200 -Body '{"lectureId":"lec_20260619_ai_001","studentNo":"2023001001","idempotencyKey":"idem_001"}' -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-Equal -Name $name -Endpoint $endpoint -Field "data.registerStatus" -Expected "SUCCESS" -Actual $response.data.registerStatus
}

Run-MockSmoke -Name "lecture register rate limited" -Method "POST" -Path "/mock-provider/lecture/register" -ExpectedCode 429 -Body '{"lectureId":"lec_20260619_ai_001","studentNo":"2023001001","idempotencyKey":"idem_001","mockScenario":"RATE_LIMITED"}' -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-ContainsText -Name $name -Endpoint $endpoint -Field "message" -ExpectedText "too many requests" -Actual $response.message
}

Run-MockSmoke -Name "campus notice normal" -Method "GET" -Path "/mock-provider/notice/list?category=exam" -ExpectedCode 200 -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.notices.Count" -Threshold 0 -Actual @($response.data.notices).Count
}

Run-MockSmoke -Name "venue reserve normal" -Method "POST" -Path "/mock-provider/venue/reserve" -ExpectedCode 200 -Body '{"venueId":"venue_report_hall_201","studentNo":"2023001001","reserveDate":"2026-06-20","timeRange":"19:00-21:00","idempotencyKey":"idem_venue_001"}' -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-Equal -Name $name -Endpoint $endpoint -Field "data.reserveStatus" -Expected "SUCCESS" -Actual $response.data.reserveStatus
}

Run-MockSmoke -Name "venue reserve conflict" -Method "POST" -Path "/mock-provider/venue/reserve" -Headers @{ "X-Mock-Scenario" = "RESERVATION_CONFLICT" } -ExpectedCode 409 -Body '{"venueId":"venue_report_hall_201","studentNo":"2023001001","reserveDate":"2026-06-20","timeRange":"19:00-21:00","idempotencyKey":"idem_venue_001"}' -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-ContainsText -Name $name -Endpoint $endpoint -Field "message" -ExpectedText "reservation conflict" -Actual $response.message
}

Run-MockSmoke -Name "library borrow normal" -Method "GET" -Path "/mock-provider/library/borrow?studentNo=2023001001" -ExpectedCode 200 -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.borrowRecords.Count" -Threshold 0 -Actual @($response.data.borrowRecords).Count
}

Run-MockSmoke -Name "library borrow downstream timeout" -Method "GET" -Path "/mock-provider/library/borrow?studentNo=2023001001&mockScenario=DOWNSTREAM_TIMEOUT" -ExpectedCode 504 -ExtraChecks {
    param($response, $name, $endpoint)
    return Assert-ContainsText -Name $name -Endpoint $endpoint -Field "message" -ExpectedText "library downstream timeout" -Actual $response.message
}

if ($script:Failed -gt 0) {
    Write-Host "Mock provider smoke test failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "All mock provider smoke tests passed." -ForegroundColor Green
exit 0
