$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$registry = Join-Path $repoRoot '_local/frontend-processes.json'
if (Test-Path -LiteralPath $registry) {
    $records = @(Get-Content -LiteralPath $registry -Raw -Encoding UTF8 | ConvertFrom-Json)
    foreach ($record in $records) {
        $process = Get-Process -Id $record.Id -ErrorAction SilentlyContinue
        if ($process -and $process.ProcessName -eq 'node' -and $process.StartTime.ToUniversalTime().ToString('o') -eq $record.StartTime) {
            Stop-Process -Id $process.Id -ErrorAction Stop
        }
    }
    '[]' | Set-Content -LiteralPath $registry -Encoding UTF8
}
Push-Location $repoRoot
try {
    docker compose --env-file _local/.env -f scripts/local/compose.yml stop
    if ($LASTEXITCODE -ne 0) { throw 'Container shutdown failed.' }
} finally {
    Pop-Location
}
