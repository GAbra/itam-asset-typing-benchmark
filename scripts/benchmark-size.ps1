param(
  [ValidateSet(10000,100000,500000,1000000)]
  [int]$Count = 10000
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$data = "data/generated/normalized-$Count.jsonl"
if (-not (Test-Path $data)) { throw "Dataset not found: $data. Generate it first." }
New-Item -ItemType Directory -Force -Path benchmark-results | Out-Null
docker compose run --rm demo java -jar target/itam-typing-demo-1.0.0-SNAPSHOT.jar verify --data $data --out "benchmark-results/verify-$Count.json"
docker compose run --rm demo java -jar target/itam-typing-demo-1.0.0-SNAPSHOT.jar benchmark --data $data --warmup 2 --runs 5 --batch 5000 --out "benchmark-results/benchmark-$Count.json"
