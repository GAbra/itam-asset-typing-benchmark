$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

function Invoke-DockerChecked {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,
        [Parameter(Mandatory = $true)]
        [string] $Description
    )

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed (docker exit code $LASTEXITCODE)."
    }
}

Write-Host "[0/5] Docker preflight"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI is not installed or is not present in PATH. Install/start Docker Desktop and run this script again."
}

& docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose is unavailable. Start/update Docker Desktop and run this script again."
}

$dockerOs = (& docker info --format '{{.OSType}}' 2>$null)
if ($LASTEXITCODE -ne 0) {
    throw @"
Docker Desktop engine is not reachable.

On Windows:
1. Start Docker Desktop.
2. Wait until Docker Desktop shows that the engine is running.
3. Make sure Linux containers are enabled.
4. Verify in PowerShell: docker info
5. Run this script again.

If docker info still refers to //./pipe/dockerDesktopLinuxEngine and says the file does not exist,
Docker Desktop is installed but its Linux engine is not running yet.
"@
}

if (($dockerOs | Out-String).Trim() -ne "linux") {
    throw "Docker is running, but not in Linux-container mode. Switch Docker Desktop to Linux containers and run again."
}
Write-Host "Docker Linux engine: OK"

Write-Host "[1/5] Build + tests"
Invoke-DockerChecked -Arguments @('compose', 'build') -Description 'Docker image build'
Invoke-DockerChecked -Arguments @('compose', 'run', '--rm', 'demo', 'mvn', '-B', 'clean', 'test', 'package') -Description 'Maven build and tests'

Write-Host "[2/5] Generate deterministic 10K dataset"
New-Item -ItemType Directory -Force -Path data/generated,benchmark-results,rules/generated | Out-Null
Invoke-DockerChecked -Arguments @('compose', 'run', '--rm', 'demo', 'java', '-jar', 'target/itam-typing-demo-1.0.0-SNAPSHOT.jar', 'generate', '--count', '10000', '--seed', '20260909', '--out', 'data/generated/normalized-10000.jsonl') -Description 'Dataset generation'

Write-Host "[3/5] Differential verification: BitSet = CEL = DMN"
Invoke-DockerChecked -Arguments @('compose', 'run', '--rm', 'demo', 'java', '-jar', 'target/itam-typing-demo-1.0.0-SNAPSHOT.jar', 'verify', '--data', 'data/generated/normalized-10000.jsonl', '--out', 'benchmark-results/verify-10000.json') -Description 'Differential verification'

Write-Host "[4/5] Simple repeatable benchmark"
Invoke-DockerChecked -Arguments @('compose', 'run', '--rm', 'demo', 'java', '-jar', 'target/itam-typing-demo-1.0.0-SNAPSHOT.jar', 'benchmark', '--data', 'data/generated/normalized-10000.jsonl', '--warmup', '2', '--runs', '5', '--batch', '2000', '--out', 'benchmark-results/benchmark-10000.json') -Description 'Benchmark'

Write-Host "[5/5] Export generated DMN"
Invoke-DockerChecked -Arguments @('compose', 'run', '--rm', 'demo', 'java', '-jar', 'target/itam-typing-demo-1.0.0-SNAPSHOT.jar', 'export-dmn', '--out', 'rules/generated/itam-typing.dmn') -Description 'DMN export'

Write-Host ""
Write-Host "DONE. Send me these files:"
Write-Host "  benchmark-results/verify-10000.json"
Write-Host "  benchmark-results/benchmark-10000.json"
