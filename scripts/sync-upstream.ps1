[CmdletBinding()]
param(
    [ValidateSet('all', 'ruoyi-vue-pro', 'yudao-ui-admin-vue3', 'yudao-mall-uniapp')]
    [string]$Project = 'all',
    [switch]$SetupOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))

function Invoke-RepoGit {
    param([string[]]$GitArguments)
    & git -C $repoRoot @GitArguments
    if ($LASTEXITCODE -ne 0) {
        throw ('Git command failed: git ' + ($GitArguments -join ' '))
    }
}

$actualRoot = (Invoke-RepoGit -GitArguments @('rev-parse', '--show-toplevel')).Trim()
if ([IO.Path]::GetFullPath($actualRoot) -ne $repoRoot) {
    throw 'Run this script from a complete yuanmall checkout.'
}

$manifest = Get-Content -LiteralPath (Join-Path $repoRoot 'upstreams.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$projects = @($manifest.projects | Where-Object { $Project -eq 'all' -or $_.path -eq $Project })
if ($projects.Count -eq 0) {
    throw 'No matching upstream project.'
}

if (-not $SetupOnly) {
    $changes = @(Invoke-RepoGit -GitArguments @('status', '--porcelain', '--untracked-files=normal'))
    if ($changes.Count -ne 0) {
        throw 'Commit or stash your local changes before syncing an upstream.'
    }
}

if ($env:OS -eq 'Windows_NT') {
    Invoke-RepoGit -GitArguments @('config', 'core.longpaths', 'true')
}

$remotes = @(Invoke-RepoGit -GitArguments @('remote'))
foreach ($entry in $projects) {
    $projectDirectory = [IO.Path]::GetFullPath((Join-Path $repoRoot $entry.path))
    $separator = [IO.Path]::DirectorySeparatorChar
    if (-not $projectDirectory.StartsWith($repoRoot + $separator, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Project directory must remain inside the yuanmall checkout.'
    }
    if (-not (Test-Path -LiteralPath $projectDirectory -PathType Container)) {
        throw ('Missing project directory: ' + $entry.path)
    }

    if ($remotes -contains $entry.remote) {
        $url = (Invoke-RepoGit -GitArguments @('remote', 'get-url', $entry.remote)).Trim()
        if ($url -ne $entry.url) {
            throw ('Unexpected URL for ' + $entry.remote + '; check upstreams.json and your Git config.')
        }
    } else {
        Invoke-RepoGit -GitArguments @('remote', 'add', $entry.remote, $entry.url)
    }

    if ($SetupOnly) {
        Write-Host ($entry.path + ' -> ' + $entry.remote + ' (' + $entry.branch + ')')
    } else {
        Write-Host ('Syncing ' + $entry.path + ' from ' + $entry.url)
        Invoke-RepoGit -GitArguments @(
            'subtree', 'pull', ('--prefix=' + $entry.path), $entry.remote, $entry.branch,
            '--squash', '-m', ('chore(' + $entry.path + '): sync upstream ' + $entry.branch)
        )
    }
}
