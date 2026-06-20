param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$script:Failed = 0
$BaseUrl = $BaseUrl.TrimEnd("/")

function Convert-ErrorResponseToJson {
    param(
        [Parameter(Mandatory = $true)]
        $Response
    )

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

function Invoke-SmokeRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [hashtable]$Headers = @{},

        [string]$Body = $null
    )

    $uri = "$BaseUrl$Path"
    $params = @{
        Uri = $uri
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
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Endpoint,

        [Parameter(Mandatory = $true)]
        [string]$Expected,

        [Parameter(Mandatory = $false)]
        $Actual
    )

    $script:Failed++
    Write-Host "[FAIL] $Name" -ForegroundColor Red
    Write-Host "       endpoint: $Endpoint"
    Write-Host "       expected: $Expected"
    Write-Host "       actual:   $Actual"
}

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Endpoint,

        [Parameter(Mandatory = $true)]
        [string]$Field,

        [Parameter(Mandatory = $false)]
        $Expected,

        [Parameter(Mandatory = $false)]
        $Actual
    )

    if ($Expected -ne $Actual) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field=$Expected" -Actual "$Field=$Actual"
        return $false
    }

    return $true
}

function Assert-GreaterThan {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Endpoint,

        [Parameter(Mandatory = $true)]
        [string]$Field,

        [Parameter(Mandatory = $false)]
        $Threshold,

        [Parameter(Mandatory = $false)]
        $Actual
    )

    if ($Actual -le $Threshold) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field>$Threshold" -Actual "$Field=$Actual"
        return $false
    }

    return $true
}

function Assert-Contains {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Endpoint,

        [Parameter(Mandatory = $true)]
        [string]$Field,

        [Parameter(Mandatory = $false)]
        $Expected,

        [Parameter(Mandatory = $false)]
        $ActualList
    )

    if (@($ActualList) -notcontains $Expected) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field contains $Expected" -Actual "$Field=$($ActualList -join ',')"
        return $false
    }

    return $true
}

function Assert-NotBlank {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Endpoint,

        [Parameter(Mandatory = $true)]
        [string]$Field,

        [Parameter(Mandatory = $false)]
        $Actual
    )

    if ($null -eq $Actual -or [string]::IsNullOrWhiteSpace([string]$Actual)) {
        Write-Fail -Name $Name -Endpoint $Endpoint -Expected "$Field is not blank" -Actual "$Field=$Actual"
        return $false
    }

    return $true
}

function Run-SmokeTest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [int]$ExpectedCode,

        [hashtable]$Headers = @{},

        [string]$Body = $null,

        [scriptblock]$ExtraChecks = $null
    )

    $endpoint = "$Method $Path"

    try {
        $response = Invoke-SmokeRequest -Method $Method -Path $Path -Headers $Headers -Body $Body
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

Write-Host "Backend smoke test target: $BaseUrl"

Run-SmokeTest `
    -Name "health" `
    -Method "GET" `
    -Path "/api/health" `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        return Assert-Equal -Name $name -Endpoint $endpoint -Field "data.status" -Expected "UP" -Actual $response.data.status
    }

Run-SmokeTest `
    -Name "database health" `
    -Method "GET" `
    -Path "/api/health/db" `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.databaseName" -Expected "apihub_agent" -Actual $response.data.databaseName
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.tableCount" -Expected 17 -Actual $response.data.tableCount) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "current user default" `
    -Method "GET" `
    -Path "/api/users/current" `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        return Assert-Equal -Name $name -Endpoint $endpoint -Field "data.id" -Expected 1 -Actual $response.data.id
    }

Run-SmokeTest `
    -Name "current user by header" `
    -Method "GET" `
    -Path "/api/users/current" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        return Assert-Equal -Name $name -Endpoint $endpoint -Field "data.id" -Expected 1 -Actual $response.data.id
    }

Run-SmokeTest `
    -Name "user list page 20" `
    -Method "GET" `
    -Path "/api/users?pageNo=1&pageSize=20" `
    -ExpectedCode 200

Run-SmokeTest `
    -Name "user list page size capped" `
    -Method "GET" `
    -Path "/api/users?pageNo=1&pageSize=200" `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        return Assert-Equal -Name $name -Endpoint $endpoint -Field "data.pageSize" -Expected 100 -Actual $response.data.pageSize
    }

Run-SmokeTest `
    -Name "switch user" `
    -Method "POST" `
    -Path "/api/users/switch" `
    -Body '{"userId":1}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        return Assert-Equal -Name $name -Endpoint $endpoint -Field "data.id" -Expected 1 -Actual $response.data.id
    }

Run-SmokeTest `
    -Name "current user not found" `
    -Method "GET" `
    -Path "/api/users/current" `
    -Headers @{ "X-Demo-User-Id" = "999999" } `
    -ExpectedCode 404

Run-SmokeTest `
    -Name "switch user bad request" `
    -Method "POST" `
    -Path "/api/users/switch" `
    -Body '{}' `
    -ExpectedCode 400

Run-SmokeTest `
    -Name "tool queryApiInfo admin success" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiInfo" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"AUTH_LOGIN","includeRateLimit":true,"includeConsumerApps":true}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.toolName" -Expected "queryApiInfo" -Actual $response.data.toolName) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.apiCode" -Expected "AUTH_LOGIN" -Actual $response.data.data.apiCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryApiCallStats admin success" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiCallStats" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"LECTURE_REGISTER","startTime":"2026-06-19 00:00:00","endTime":"2026-06-19 23:59:59"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.toolName" -Expected "queryApiCallStats" -Actual $response.data.toolName) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.apiCode" -Expected "LECTURE_REGISTER" -Actual $response.data.data.apiCode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.data.totalCallCount" -Threshold 0 -Actual $response.data.data.totalCallCount) -and $ok
        $ok = (Assert-NotBlank -Name $name -Endpoint $endpoint -Field "data.data.riskLevel" -Actual $response.data.data.riskLevel) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryApiInfo api not found" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiInfo" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"UNKNOWN_API"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "API_NOT_FOUND" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryApiCallStats invalid time range" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiCallStats" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"LECTURE_REGISTER","startTime":"2026-06-20 00:00:00","endTime":"2026-06-19 00:00:00"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "INVALID_ARGUMENT" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryApiInfo permission denied" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiInfo" `
    -Headers @{ "X-Demo-User-Id" = "4" } `
    -Body '{"apiCode":"LIBRARY_BORROW"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "PERMISSION_DENIED" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryGatewayLogs auth 403 success" `
    -Method "POST" `
    -Path "/api/dev/tools/queryGatewayLogs" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"AUTH_LOGIN","startTime":"2026-06-19 00:00:00","endTime":"2026-06-19 23:59:59","httpStatus":403,"limit":20}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.toolName" -Expected "queryGatewayLogs" -Actual $response.data.toolName) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.apiCode" -Expected "AUTH_LOGIN" -Actual $response.data.data.apiCode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.data.totalMatched" -Threshold 0 -Actual $response.data.data.totalMatched) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.evidenceItems.Count" -Threshold 0 -Actual @($response.data.evidenceItems).Count) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryGatewayLogs invalid time range" `
    -Method "POST" `
    -Path "/api/dev/tools/queryGatewayLogs" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"AUTH_LOGIN","startTime":"2026-06-20 00:00:00","endTime":"2026-06-19 00:00:00"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "INVALID_ARGUMENT" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryRateLimitRule lecture register success" `
    -Method "POST" `
    -Path "/api/dev/tools/queryRateLimitRule" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"LECTURE_REGISTER","includeInactive":false}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.toolName" -Expected "queryRateLimitRule" -Actual $response.data.toolName) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.apiCode" -Expected "LECTURE_REGISTER" -Actual $response.data.data.apiCode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.data.activeRuleCount" -Threshold 0 -Actual $response.data.data.activeRuleCount) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.evidenceItems.Count" -Threshold 0 -Actual @($response.data.evidenceItems).Count) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryRateLimitRule api not found" `
    -Method "POST" `
    -Path "/api/dev/tools/queryRateLimitRule" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"UNKNOWN_API"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "API_NOT_FOUND" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryGatewayLogs permission denied" `
    -Method "POST" `
    -Path "/api/dev/tools/queryGatewayLogs" `
    -Headers @{ "X-Demo-User-Id" = "4" } `
    -Body '{"apiCode":"LIBRARY_BORROW"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "PERMISSION_DENIED" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryAlertEvents lecture register success" `
    -Method "POST" `
    -Path "/api/dev/tools/queryAlertEvents" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"LECTURE_REGISTER","startTime":"2026-06-19 00:00:00","endTime":"2026-06-19 23:59:59","limit":20}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.toolName" -Expected "queryAlertEvents" -Actual $response.data.toolName) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.apiCode" -Expected "LECTURE_REGISTER" -Actual $response.data.data.apiCode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.data.totalMatched" -Threshold 0 -Actual $response.data.data.totalMatched) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.evidenceItems.Count" -Threshold 0 -Actual @($response.data.evidenceItems).Count) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryCampusEvents lecture register success" `
    -Method "POST" `
    -Path "/api/dev/tools/queryCampusEvents" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"LECTURE_REGISTER","startTime":"2026-06-19 00:00:00","endTime":"2026-06-19 23:59:59","includeRelatedApis":true,"limit":20}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.toolName" -Expected "queryCampusEvents" -Actual $response.data.toolName) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.apiCode" -Expected "LECTURE_REGISTER" -Actual $response.data.data.apiCode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.data.totalMatched" -Threshold 0 -Actual $response.data.data.totalMatched) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.evidenceItems.Count" -Threshold 0 -Actual @($response.data.evidenceItems).Count) -and $ok
        $relatedApiCodes = @($response.data.data.relatedApiCodes)
        $ok = (Assert-Contains -Name $name -Endpoint $endpoint -Field "data.data.relatedApiCodes" -Expected "LECTURE_REGISTER" -ActualList $relatedApiCodes) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryAlertEvents invalid time range" `
    -Method "POST" `
    -Path "/api/dev/tools/queryAlertEvents" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"LECTURE_REGISTER","startTime":"2026-06-20 00:00:00","endTime":"2026-06-19 00:00:00"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "INVALID_ARGUMENT" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryCampusEvents api not found" `
    -Method "POST" `
    -Path "/api/dev/tools/queryCampusEvents" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"UNKNOWN_API"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "API_NOT_FOUND" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryAlertEvents permission denied" `
    -Method "POST" `
    -Path "/api/dev/tools/queryAlertEvents" `
    -Headers @{ "X-Demo-User-Id" = "4" } `
    -Body '{"apiCode":"LIBRARY_BORROW"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "PERMISSION_DENIED" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryApiDocs auth signature success" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiDocs" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"AUTH_LOGIN","keyword":"signature","limit":5}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.toolName" -Expected "queryApiDocs" -Actual $response.data.toolName) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.apiCode" -Expected "AUTH_LOGIN" -Actual $response.data.data.apiCode) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.searchMode" -Expected "MYSQL_KEYWORD" -Actual $response.data.data.searchMode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.data.totalMatched" -Threshold 0 -Actual $response.data.data.totalMatched) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.evidenceItems.Count" -Threshold 0 -Actual @($response.data.evidenceItems).Count) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryApiDocs lecture rate success" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiDocs" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"LECTURE_REGISTER","keyword":"rate","limit":5}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.toolName" -Expected "queryApiDocs" -Actual $response.data.toolName) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.apiCode" -Expected "LECTURE_REGISTER" -Actual $response.data.data.apiCode) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.data.searchMode" -Expected "MYSQL_KEYWORD" -Actual $response.data.data.searchMode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryApiDocs api not found" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiDocs" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"apiCode":"UNKNOWN_API","keyword":"signature"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "API_NOT_FOUND" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool queryApiDocs permission denied" `
    -Method "POST" `
    -Path "/api/dev/tools/queryApiDocs" `
    -Headers @{ "X-Demo-User-Id" = "4" } `
    -Body '{"apiCode":"LIBRARY_BORROW","keyword":"timeout"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "PERMISSION_DENIED" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool chain scenarios list" `
    -Method "GET" `
    -Path "/api/dev/eval/tool-chain/scenarios" `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $scenarioCodes = @($response.data | ForEach-Object { $_.scenarioCode })
        $ok = Assert-Contains -Name $name -Endpoint $endpoint -Field "data.scenarioCode" -Expected "AUTH_LOGIN_403_DIAG" -ActualList $scenarioCodes
        $ok = (Assert-Contains -Name $name -Endpoint $endpoint -Field "data.scenarioCode" -Expected "LECTURE_REGISTER_PEAK" -ActualList $scenarioCodes) -and $ok
        $ok = (Assert-Contains -Name $name -Endpoint $endpoint -Field "data.scenarioCode" -Expected "VENUE_RESERVE_IDEMPOTENCY" -ActualList $scenarioCodes) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool chain run auth login 403" `
    -Method "POST" `
    -Path "/api/dev/eval/tool-chain/run" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"scenarioCode":"AUTH_LOGIN_403_DIAG","startTime":"2026-06-19 00:00:00","endTime":"2026-06-19 23:59:59"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.scenarioCode" -Expected "AUTH_LOGIN_403_DIAG" -Actual $response.data.scenarioCode
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.apiCode" -Expected "AUTH_LOGIN" -Actual $response.data.apiCode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.stepCount" -Threshold 3 -Actual $response.data.stepCount) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.mergedEvidenceItems.Count" -Threshold 0 -Actual @($response.data.mergedEvidenceItems).Count) -and $ok
        $ok = (Assert-NotBlank -Name $name -Endpoint $endpoint -Field "data.templateConclusion" -Actual $response.data.templateConclusion) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool chain run lecture peak" `
    -Method "POST" `
    -Path "/api/dev/eval/tool-chain/run" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"scenarioCode":"LECTURE_REGISTER_PEAK","startTime":"2026-06-19 00:00:00","endTime":"2026-06-19 23:59:59"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.scenarioCode" -Expected "LECTURE_REGISTER_PEAK" -Actual $response.data.scenarioCode
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.apiCode" -Expected "LECTURE_REGISTER" -Actual $response.data.apiCode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.stepCount" -Threshold 4 -Actual $response.data.stepCount) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.mergedEvidenceItems.Count" -Threshold 0 -Actual @($response.data.mergedEvidenceItems).Count) -and $ok
        $ok = (Assert-NotBlank -Name $name -Endpoint $endpoint -Field "data.templateConclusion" -Actual $response.data.templateConclusion) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool chain run unknown scenario" `
    -Method "POST" `
    -Path "/api/dev/eval/tool-chain/run" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"scenarioCode":"UNKNOWN_SCENARIO"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "SCENARIO_NOT_FOUND" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "tool chain run invalid time range" `
    -Method "POST" `
    -Path "/api/dev/eval/tool-chain/run" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"scenarioCode":"AUTH_LOGIN_403_DIAG","startTime":"2026-06-20 00:00:00","endTime":"2026-06-19 00:00:00"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "INVALID_ARGUMENT" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "agent run lecture peak" `
    -Method "POST" `
    -Path "/api/agent/run" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"scenarioCode":"LECTURE_REGISTER_PEAK","question":"Analyze lecture registration peak risk","startTime":"2026-06-19 00:00:00","endTime":"2026-06-19 23:59:59"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.scenarioCode" -Expected "LECTURE_REGISTER_PEAK" -Actual $response.data.scenarioCode) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.apiCode" -Expected "LECTURE_REGISTER" -Actual $response.data.apiCode) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.sessionId" -Threshold 0 -Actual $response.data.sessionId) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.reportId" -Threshold 0 -Actual $response.data.reportId) -and $ok
        $ok = (Assert-GreaterThan -Name $name -Endpoint $endpoint -Field "data.evidenceItems.Count" -Threshold 0 -Actual @($response.data.evidenceItems).Count) -and $ok
        $ok = (Assert-NotBlank -Name $name -Endpoint $endpoint -Field "data.finalAnswer" -Actual $response.data.finalAnswer) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "agent run question matched auth 403" `
    -Method "POST" `
    -Path "/api/agent/run" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"question":"AUTH_LOGIN has many 403 signature token failures","startTime":"2026-06-19 00:00:00","endTime":"2026-06-19 23:59:59"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $true -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.scenarioCode" -Expected "AUTH_LOGIN_403_DIAG" -Actual $response.data.scenarioCode) -and $ok
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.apiCode" -Expected "AUTH_LOGIN" -Actual $response.data.apiCode) -and $ok
        $ok = (Assert-NotBlank -Name $name -Endpoint $endpoint -Field "data.finalAnswer" -Actual $response.data.finalAnswer) -and $ok
        return $ok
    }

Run-SmokeTest `
    -Name "agent run scenario not matched" `
    -Method "POST" `
    -Path "/api/agent/run" `
    -Headers @{ "X-Demo-User-Id" = "1" } `
    -Body '{"question":"general unrelated capacity review"}' `
    -ExpectedCode 200 `
    -ExtraChecks {
        param($response, $name, $endpoint)
        $ok = Assert-Equal -Name $name -Endpoint $endpoint -Field "data.success" -Expected $false -Actual $response.data.success
        $ok = (Assert-Equal -Name $name -Endpoint $endpoint -Field "data.errorCode" -Expected "SCENARIO_NOT_MATCHED" -Actual $response.data.errorCode) -and $ok
        return $ok
    }

$agentRunStreamName = "agent run stream lecture peak"
$agentRunStreamEndpoint = "POST /api/agent/run/stream"
try {
    $agentRunStreamBody = [ordered]@{
        scenarioCode = "LECTURE_REGISTER_PEAK"
        question = "Stream lecture registration peak risk"
        startTime = "2026-06-19 00:00:00"
        endTime = "2026-06-19 23:59:59"
    } | ConvertTo-Json -Compress
    $agentRunStreamBodyFile = [System.IO.Path]::GetTempFileName()
    Set-Content -LiteralPath $agentRunStreamBodyFile -Value $agentRunStreamBody -NoNewline -Encoding utf8
    $agentRunStreamOutput = & curl.exe -s -N -X POST "$BaseUrl/api/agent/run/stream" `
        -H "Content-Type: application/json" `
        -H "X-Demo-User-Id: 1" `
        --data-binary "@$agentRunStreamBodyFile"
    Remove-Item -LiteralPath $agentRunStreamBodyFile -Force
    $agentRunStreamText = $agentRunStreamOutput -join "`n"

    $agentRunStreamOk = $true
    foreach ($expectedEvent in @("agent_start", "tool_step", "evidence", "answer", "done")) {
        if ($agentRunStreamText -notmatch "event:\s*$expectedEvent") {
            Write-Fail -Name $agentRunStreamName -Endpoint $agentRunStreamEndpoint -Expected "SSE output contains event $expectedEvent" -Actual $agentRunStreamText
            $agentRunStreamOk = $false
            break
        }
    }

    if ($agentRunStreamOk) {
        Write-Host "[PASS] $agentRunStreamName" -ForegroundColor Green
    }
}
catch {
    Write-Fail -Name $agentRunStreamName -Endpoint $agentRunStreamEndpoint -Expected "reachable SSE response" -Actual $_.Exception.Message
}

if ($script:Failed -gt 0) {
    Write-Host "Smoke test failed: $script:Failed failure(s)." -ForegroundColor Red
    exit 1
}

Write-Host "All backend smoke tests passed." -ForegroundColor Green
exit 0
