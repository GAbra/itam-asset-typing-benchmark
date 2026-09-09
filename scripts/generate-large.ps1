param(
  [ValidateSet(100000,500000,1000000)]
  [int]$Count = 100000
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
New-Item -ItemType Directory -Force -Path data/generated | Out-Null
docker compose run --rm demo java -jar target/itam-typing-demo-1.0.0-SNAPSHOT.jar generate --count $Count --seed 20260909 --out "data/generated/normalized-$Count.jsonl"
