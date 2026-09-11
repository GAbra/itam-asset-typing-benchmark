param(
  [ValidateSet(10000,100000,500000,1000000)]
  [int]$Count = 10000
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$data = "data/generated/normalized-$Count.jsonl"
if (-not (Test-Path $data)) { throw "Dataset not found: $data. Generate it first." }
New-Item -ItemType Directory -Force -Path results/local | Out-Null
docker compose run --rm demo java -jar target/itam-asset-typing-benchmark-2.0.0.jar verify --data $data --out "results/local/verify-$Count.json"
if ($LASTEXITCODE -ne 0) { throw "Verification failed; benchmark was not started (exit $LASTEXITCODE)." }
$batchSize = if ($Count -eq 10000) {2000} elseif ($Count -eq 100000) {5000} else {10000}
docker compose run --rm demo java -jar target/itam-asset-typing-benchmark-2.0.0.jar benchmark --data $data --warmup 2 --runs 5 --batch $batchSize --out "results/local/benchmark-$Count.json"
if ($LASTEXITCODE -ne 0) { throw "Benchmark failed (exit $LASTEXITCODE)." }
