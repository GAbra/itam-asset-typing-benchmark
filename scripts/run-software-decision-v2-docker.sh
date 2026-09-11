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
  -e SOFTWARE_COUNT="${SOFTWARE_COUNT:-100000}" \
  -e SEED="${SEED:-20260915}" \
  -e CONFLICT_WINDOW="${CONFLICT_WINDOW:-80}" \
  -e STRICT_GATE="${STRICT_GATE:-1}" \
  -e OUT_DIR="${OUT_DIR:-}" \
  -v "$HOST_ROOT:/workspace" \
  -w /workspace \
  "$IMAGE" sh -c 'git config --global --add safe.directory /workspace && exec sh scripts/run-software-decision-v2.sh'
