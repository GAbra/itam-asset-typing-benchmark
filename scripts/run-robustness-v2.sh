#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

if [ "${ALLOW_DIRTY:-0}" != "1" ] && [ -n "$(git status --porcelain)" ]; then
  echo "Refusing to run robustness gate on a dirty worktree. Commit/stash changes or set ALLOW_DIRTY=1." >&2
  exit 2
fi

SEED=${SEED:-20260911}
ROBUSTNESS_COUNT=${ROBUSTNESS_COUNT:-100000}
DISTRIBUTION_COUNT=${DISTRIBUTION_COUNT:-50000}
CONFLICT_WINDOW=${CONFLICT_WINDOW:-80}
STRICT_GATE=${STRICT_GATE:-1}
SOURCE_COMMIT=$(git rev-parse HEAD)
SHORT_COMMIT=$(printf '%.12s' "$SOURCE_COMMIT")
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT_DIR=${OUT_DIR:-results/robustness-v2/$SHORT_COMMIT-$STAMP}
mkdir -p "$OUT_DIR/accuracy" "$OUT_DIR/distributions" "$OUT_DIR/provenance"

export JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-"-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8"}

echo "Building application..."
mvn -q clean package
JAR=target/itam-asset-typing-benchmark-2.0.0.jar

run_case() {
  noise=$1
  distribution=$2
  count=$3
  case_dir=$4
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
    --count "$count" --seed "$SEED" --noise "$noise" --distribution "$distribution" \
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
  java -cp "$JAR" ru.itam.typing.realistic.AccuracyEvaluationCli \
    --data "$hold_normalized" --truth "$hold_truth" --rules rules/canonical-rules.yaml \
    --conflict-window "$CONFLICT_WINDOW" --out "$case_dir/conservative.json" > "$case_dir/conservative.log"
}

echo "Robustness gate across noise regimes..."
for noise in clean light moderate stress severe; do
  run_case "$noise" balanced "$ROBUSTNESS_COUNT" "$OUT_DIR/accuracy/$noise"
done

echo "Robustness gate across population distributions under stress..."
for distribution in device-heavy identity-heavy software-heavy; do
  run_case stress "$distribution" "$DISTRIBUTION_COUNT" "$OUT_DIR/distributions/$distribution"
done

{
  echo "protocol=robustness-abstention-v2"
  echo "sourceCommit=$SOURCE_COMMIT"
  echo "seed=$SEED"
  echo "robustnessCount=$ROBUSTNESS_COUNT"
  echo "distributionCount=$DISTRIBUTION_COUNT"
  echo "conflictPriorityWindow=$CONFLICT_WINDOW"
  echo "strictGate=$STRICT_GATE"
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

python3 scripts/compare-robustness-v2.py "$OUT_DIR" --window "$CONFLICT_WINDOW"
find "$OUT_DIR" -type f ! -name SHA256SUMS.txt -print0 | sort -z | xargs -0 sha256sum > "$OUT_DIR/SHA256SUMS.txt"

if [ "$STRICT_GATE" = "1" ]; then
  python3 scripts/validate-robustness-v2.py "$OUT_DIR" --window "$CONFLICT_WINDOW" --strict
else
  python3 scripts/validate-robustness-v2.py "$OUT_DIR" --window "$CONFLICT_WINDOW"
fi

echo "DONE: $OUT_DIR"
