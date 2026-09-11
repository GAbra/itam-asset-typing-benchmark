#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

if [ "${ALLOW_DIRTY:-0}" != "1" ] && [ -n "$(git status --porcelain)" ]; then
  echo "Refusing to run conflict-window sweep on a dirty worktree. Commit/stash changes or set ALLOW_DIRTY=1." >&2
  exit 2
fi

SEED=${SEED:-20260912}
CALIBRATION_COUNT=${CALIBRATION_COUNT:-200000}
WINDOWS=${WINDOWS:-"20 40 60 80 100 120"}
STRICT_GATE=${STRICT_GATE:-1}
SOURCE_COMMIT=$(git rev-parse HEAD)
SHORT_COMMIT=$(printf '%.12s' "$SOURCE_COMMIT")
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT_DIR=${OUT_DIR:-results/conflict-window-sweep-v2/$SHORT_COMMIT-$STAMP}
mkdir -p "$OUT_DIR/accuracy" "$OUT_DIR/provenance"

export JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-"-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8"}

echo "Building application..."
mvn -q clean package
JAR=target/itam-asset-typing-benchmark-2.0.0.jar

run_case() {
  noise=$1
  case_dir=$2
  mkdir -p "$case_dir"

  raw="$case_dir/raw.jsonl"
  truth="$case_dir/truth.jsonl"
  normalized="$case_dir/normalized.jsonl"
  train_raw="$case_dir/train-raw.jsonl"
  train_truth="$case_dir/train-truth.jsonl"
  hold_raw="$case_dir/holdout-raw.jsonl"
  hold_truth="$case_dir/holdout-truth.jsonl"
  hold_normalized="$case_dir/holdout-normalized.jsonl"

  java -cp "$JAR" ru.itam.typing.realistic.RealisticWorkloadCli all \
    --count "$CALIBRATION_COUNT" --seed "$SEED" --noise "$noise" --distribution balanced \
    --raw "$raw" --truth "$truth" --out "$normalized" > "$case_dir/generation.log"

  java -cp "$JAR" ru.itam.typing.realistic.HoldoutSplitCli \
    --raw "$raw" --truth "$truth" --train-raw "$train_raw" --train-truth "$train_truth" \
    --holdout-raw "$hold_raw" --holdout-truth "$hold_truth" --holdout-pct 20 \
    --out "$case_dir/split.json" > "$case_dir/split.log"

  java -cp "$JAR" ru.itam.typing.realistic.RealisticWorkloadCli materialize \
    --raw "$hold_raw" --truth "$hold_truth" --out "$hold_normalized" > "$case_dir/materialize.log"

  java -cp "$JAR" ru.itam.typing.realistic.AccuracyEvaluationCli \
    --data "$hold_normalized" --truth "$hold_truth" --rules rules/canonical-rules.yaml \
    --conflict-window 0 --out "$case_dir/baseline.json" > "$case_dir/baseline.log"

  for window in $WINDOWS; do
    echo "  noise=$noise window=$window"
    java -cp "$JAR" ru.itam.typing.realistic.AccuracyEvaluationCli \
      --data "$hold_normalized" --truth "$hold_truth" --rules rules/canonical-rules.yaml \
      --conflict-window "$window" --out "$case_dir/window-$window.json" > "$case_dir/window-$window.log"
  done
}

echo "Conflict-window calibration sweep: $WINDOWS"
for noise in clean light moderate stress severe; do
  echo "noise=$noise"
  run_case "$noise" "$OUT_DIR/accuracy/$noise"
done

{
  echo "protocol=conflict-window-calibration-v2"
  echo "sourceCommit=$SOURCE_COMMIT"
  echo "seed=$SEED"
  echo "calibrationCount=$CALIBRATION_COUNT"
  echo "windows=$WINDOWS"
  echo "strictGate=$STRICT_GATE"
  echo "selectionRule=clean unchanged; aggregate abstention precision >= 0.90; stress full AUTO coverage >= 0.90; severe full AUTO coverage >= 0.85; then minimize aggregate wrong FULL AUTO rate"
  echo "javaToolOptions=$JAVA_TOOL_OPTIONS"
  echo "dockerImageId=${DOCKER_IMAGE_ID:-unknown}"
  echo "completedUtc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$OUT_DIR/provenance/protocol.env"

(java -version 2>&1 || true) > "$OUT_DIR/provenance/java.txt"
(mvn -version 2>&1 || true) > "$OUT_DIR/provenance/maven.txt"
(uname -a 2>&1 || true) > "$OUT_DIR/provenance/uname.txt"
(nproc 2>&1 || true) > "$OUT_DIR/provenance/nproc.txt"
(cat /proc/meminfo 2>/dev/null || true) > "$OUT_DIR/provenance/meminfo.txt"
(git status --porcelain=v1 2>&1 || true) > "$OUT_DIR/provenance/git-status.txt"

python3 scripts/summarize-conflict-window-sweep.py "$OUT_DIR" --windows $WINDOWS
find "$OUT_DIR" -type f ! -name SHA256SUMS.txt -print0 | sort -z | xargs -0 sha256sum > "$OUT_DIR/SHA256SUMS.txt"

if [ "$STRICT_GATE" = "1" ]; then
  python3 scripts/validate-conflict-window-sweep.py "$OUT_DIR" --windows $WINDOWS --strict
else
  python3 scripts/validate-conflict-window-sweep.py "$OUT_DIR" --windows $WINDOWS
fi

echo "DONE: $OUT_DIR"
