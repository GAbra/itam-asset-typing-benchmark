$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
docker compose build
docker compose run --rm demo mvn -B clean test package
