#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

if HOST_ROOT=$(pwd -W 2>/dev/null); then
  :
else
  HOST_ROOT=$ROOT
fi

IMAGE=${IMAGE:-itam-asset-typing-research-v2:local}
echo "Building $IMAGE..."
docker build -t "$IMAGE" .
DOCKER_IMAGE_ID=$(docker image inspect "$IMAGE" --format '{{.Id}}')

exec docker run --rm \
  --cpus=4 \
  --memory=4g \
  --memory-swap=4g \
  -e DOCKER_IMAGE_ID="$DOCKER_IMAGE_ID" \
  -e ACCURACY_COUNT="${ACCURACY_COUNT:-100000}" \
  -e DISTRIBUTION_COUNT="${DISTRIBUTION_COUNT:-50000}" \
  -e PERF_COUNT="${PERF_COUNT:-500000}" \
  -e SEED="${SEED:-20260910}" \
  -e WARMUP="${WARMUP:-2}" \
  -e RUNS="${RUNS:-6}" \
  -e BATCH="${BATCH:-10000}" \
  -e RUN_JMH="${RUN_JMH:-1}" \
  -e OUT_DIR="${OUT_DIR:-}" \
  -v "$HOST_ROOT:/workspace" \
  -w /workspace \
  "$IMAGE" sh scripts/run-research-v2.sh
