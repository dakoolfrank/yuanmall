[CmdletBinding()]
param([switch]$Rebuild, [switch]$NoFrontends)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
# NoFrontends is retained for compatibility; frontends always run separately.
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
        & docker @compose up -d --force-recreate backend
    } else {
        & docker @compose up -d backend
    }
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed.' }
    Write-Host 'API: http://127.0.0.1:48080'
    Write-Host 'Customer WeChat: pnpm --dir yudao-mall-uniapp dev:mp-weixin'
    Write-Host 'Owner WeChat: pnpm --dir yudao-ui-admin-uniapp dev:mp-weixin'
} finally {
    Pop-Location
}
