#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."

docker compose build
docker compose run --rm demo mvn -B clean test package
mkdir -p data/generated results/local rules/generated
docker compose run --rm demo java -jar target/itam-asset-typing-benchmark-1.0.0.jar generate --count 10000 --seed 20260909 --out data/generated/normalized-10000.jsonl
docker compose run --rm demo java -jar target/itam-asset-typing-benchmark-1.0.0.jar verify --data data/generated/normalized-10000.jsonl --out results/local/verify-10000.json
docker compose run --rm demo java -jar target/itam-asset-typing-benchmark-1.0.0.jar benchmark --data data/generated/normalized-10000.jsonl --warmup 2 --runs 5 --batch 2000 --out results/local/benchmark-10000.json
docker compose run --rm demo java -jar target/itam-asset-typing-benchmark-1.0.0.jar export-dmn --out rules/generated/itam-typing.dmn
printf '\nDONE. Results: results/local/verify-10000.json and results/local/benchmark-10000.json\n'
