$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command,
        [Parameter(Mandatory = $true)]
        [string]$FailureMessage
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (exit code $LASTEXITCODE)"
    }
}

$image = "itam-asset-typing-benchmark:prebuilt"

Write-Host "[0/5] Preflight"
docker image inspect $image *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Image '$image' is not loaded. First run: docker load -i .\itam-asset-typing-benchmark-prebuilt.tar"
}

$projectRoot = (Resolve-Path $PSScriptRoot).Path
$mount = "${projectRoot}:/workspace"
New-Item -ItemType Directory -Force -Path data/generated, results/local, rules/generated | Out-Null

Write-Host "[1/5] Verify runtime image"
Invoke-Native { docker run --rm $image --help } "Runtime image failed"

Write-Host "[2/5] Generate deterministic 10K dataset"
Invoke-Native { docker run --rm -v $mount -w /workspace $image generate --count 10000 --seed 20260909 --out data/generated/normalized-10000.jsonl } "Dataset generation failed"

Write-Host "[3/5] Differential verification: BitSet = CEL = DMN"
Invoke-Native { docker run --rm -v $mount -w /workspace $image verify --data data/generated/normalized-10000.jsonl --out results/local/verify-10000.json } "Differential verification failed"

Write-Host "[4/5] Simple repeatable benchmark"
Invoke-Native { docker run --rm -v $mount -w /workspace $image benchmark --data data/generated/normalized-10000.jsonl --warmup 2 --runs 5 --batch 2000 --out results/local/benchmark-10000.json } "Benchmark failed"

Write-Host "[5/5] Export generated DMN"
Invoke-Native { docker run --rm -v $mount -w /workspace $image export-dmn --out rules/generated/itam-typing.dmn } "DMN export failed"

Write-Host ""
Write-Host "DONE. Results:"
Write-Host "  results/local/verify-10000.json"
Write-Host "  results/local/benchmark-10000.json"
