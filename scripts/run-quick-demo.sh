#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."

docker compose build
docker compose run --rm demo mvn -B clean test package
mkdir -p data/generated benchmark-results rules/generated
docker compose run --rm demo java -jar target/itam-typing-demo-1.0.0-SNAPSHOT.jar generate --count 10000 --seed 20260909 --out data/generated/normalized-10000.jsonl
docker compose run --rm demo java -jar target/itam-typing-demo-1.0.0-SNAPSHOT.jar verify --data data/generated/normalized-10000.jsonl --out benchmark-results/verify-10000.json
docker compose run --rm demo java -jar target/itam-typing-demo-1.0.0-SNAPSHOT.jar benchmark --data data/generated/normalized-10000.jsonl --warmup 2 --runs 5 --batch 2000 --out benchmark-results/benchmark-10000.json
docker compose run --rm demo java -jar target/itam-typing-demo-1.0.0-SNAPSHOT.jar export-dmn --out rules/generated/itam-typing.dmn
printf '\nDONE. Send benchmark-results/verify-10000.json and benchmark-results/benchmark-10000.json\n'
