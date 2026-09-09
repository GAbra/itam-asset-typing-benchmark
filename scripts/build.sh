#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
docker compose build
docker compose run --rm demo mvn -B clean test package
