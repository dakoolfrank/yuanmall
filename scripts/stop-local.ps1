$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    docker compose --env-file _local/.env -f scripts/local/compose.yml stop
    if ($LASTEXITCODE -ne 0) { throw 'Container shutdown failed.' }
} finally {
    Pop-Location
}
