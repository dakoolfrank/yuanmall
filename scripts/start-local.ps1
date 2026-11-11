[CmdletBinding()]
param([switch]$Rebuild, [switch]$NoFrontends)

$ErrorActionPreference = 'Stop'
function Test-LocalPort {
    param([int]$Port)
    $client = New-Object Net.Sockets.TcpClient
    try {
        $pending = $client.ConnectAsync('127.0.0.1', $Port)
        return $pending.Wait(1000) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}
$repoRoot = Split-Path -Parent $PSScriptRoot
$previousEnvironment = @{}
foreach ($name in @('VITE_OPEN', 'VITE_BASE_URL', 'UNI_INPUT_DIR', 'UNI_OUTPUT_DIR')) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location $repoRoot
try {
    foreach ($sqlFile in @('member.sql', 'pay.local.sql', 'mall.sql')) {
        $sqlPath = Join-Path $repoRoot ('ruoyi-vue-pro/sql/local/extracted/' + $sqlFile)
        if (-not (Test-Path -LiteralPath $sqlPath -PathType Leaf)) {
            throw ('Missing local database script: ' + $sqlPath)
        }
    }
    python scripts/local/prepare-runtime.py
    if ($LASTEXITCODE -ne 0) { throw 'Local configuration preparation failed.' }
    $compose = @('compose', '--env-file', '_local/.env', '-f', 'scripts/local/compose.yml')
    & docker @compose up -d --wait mysql redis
    if ($LASTEXITCODE -ne 0) { throw 'Database or Redis startup failed.' }
    $buildRequired = $Rebuild -or -not (Test-Path 'ruoyi-vue-pro/yudao-server/target/yudao-server.jar' -PathType Leaf)
    if ($buildRequired) {
        & docker @compose run --rm maven
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    }
    if ($buildRequired) {
        & docker @compose up -d --force-recreate backend
    } else {
        & docker @compose up -d backend
    }
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed.' }
    if (-not $NoFrontends) {
        $node = (Get-Command node -ErrorAction Stop).Source
        $env:VITE_OPEN = 'false'
        $env:VITE_BASE_URL = 'http://127.0.0.1:48080'
        $adminRoot = Join-Path $repoRoot 'yudao-ui-admin-vue3'
        $mallRoot = Join-Path $repoRoot 'yudao-mall-uniapp'
        $logs = Join-Path $repoRoot '_local/logs'
        $processes = @()
        if (-not (Test-LocalPort -Port 5173)) {
            if (-not (Test-Path (Join-Path $adminRoot 'node_modules/.modules.yaml'))) {
                Push-Location $adminRoot
                try { pnpm install --frozen-lockfile; if ($LASTEXITCODE -ne 0) { throw 'Admin dependencies failed.' } } finally { Pop-Location }
            }
            $arguments = @('node_modules/vite/bin/vite.js', '--mode', 'env.local', '--host', '127.0.0.1', '--port', '5173', '--strictPort')
            $processes += Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $adminRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'admin.stdout.log') -RedirectStandardError (Join-Path $logs 'admin.stderr.log') -PassThru
        }
        if (-not (Test-LocalPort -Port 5300)) {
            if (-not (Test-Path (Join-Path $mallRoot 'node_modules/.modules.yaml'))) {
                Push-Location $mallRoot
                try { pnpm install; if ($LASTEXITCODE -ne 0) { throw 'Mall dependencies failed.' } } finally { Pop-Location }
            }
            $env:UNI_INPUT_DIR = $mallRoot
            $env:UNI_OUTPUT_DIR = Join-Path $mallRoot 'unpackage/dev/h5'
            $processes += Start-Process -FilePath $node -ArgumentList @('node_modules/@dcloudio/vite-plugin-uni/bin/uni.js', '-p', 'h5', '--host', '127.0.0.1', '--port', '5300') -WorkingDirectory $mallRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'mall.stdout.log') -RedirectStandardError (Join-Path $logs 'mall.stderr.log') -PassThru
        }
        if ($processes.Count -gt 0) {
            $existingProcesses = @()
            if (Test-Path '_local/frontend-processes.json') {
                $existingProcesses = @(Get-Content '_local/frontend-processes.json' -Raw -Encoding UTF8 | ConvertFrom-Json)
            }
            $newProcesses = @($processes | ForEach-Object { [pscustomobject]@{Id=$_.Id;ProcessName=$_.ProcessName;StartTime=$_.StartTime.ToUniversalTime().ToString('o')} })
            @($existingProcesses + $newProcesses) | ConvertTo-Json | Set-Content -LiteralPath '_local/frontend-processes.json' -Encoding UTF8
        }
    }
    Write-Host 'Admin: http://127.0.0.1:5173'
    Write-Host 'Mall:  http://127.0.0.1:5300'
    Write-Host 'API:   http://127.0.0.1:48080'
} finally {
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    Pop-Location
}
