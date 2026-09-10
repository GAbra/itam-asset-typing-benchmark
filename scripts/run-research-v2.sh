#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

if [ "${ALLOW_DIRTY:-0}" != "1" ] && [ -n "$(git status --porcelain)" ]; then
  echo "Refusing to benchmark a dirty worktree. Commit/stash changes or set ALLOW_DIRTY=1." >&2
  exit 2
fi

SEED=${SEED:-20260910}
ACCURACY_COUNT=${ACCURACY_COUNT:-100000}
DISTRIBUTION_COUNT=${DISTRIBUTION_COUNT:-50000}
PERF_COUNT=${PERF_COUNT:-500000}
WARMUP=${WARMUP:-2}
RUNS=${RUNS:-6}
BATCH=${BATCH:-10000}
RUN_JMH=${RUN_JMH:-1}
SOURCE_COMMIT=$(git rev-parse HEAD)
SHORT_COMMIT=$(printf '%.12s' "$SOURCE_COMMIT")
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT_DIR=${OUT_DIR:-results/research-v2/$SHORT_COMMIT-$STAMP}
mkdir -p "$OUT_DIR/accuracy" "$OUT_DIR/distributions" "$OUT_DIR/performance" "$OUT_DIR/provenance"

export JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-"-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8"}

echo "Building application + JMH artifacts..."
mvn -q -Pjmh clean package
JAR=target/itam-asset-typing-benchmark-1.0.0.jar
JMH_JAR=target/itam-asset-typing-benchmark-1.0.0-jmh.jar

run_accuracy_case() {
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
    --out "$case_dir/accuracy.json" > "$case_dir/accuracy.log"
}

echo "Accuracy sensitivity across noise regimes..."
for noise in clean light moderate stress severe; do
  run_accuracy_case "$noise" balanced "$ACCURACY_COUNT" "$OUT_DIR/accuracy/$noise"
done

echo "Accuracy sensitivity across profile distributions..."
for distribution in device-heavy identity-heavy software-heavy; do
  run_accuracy_case stress "$distribution" "$DISTRIBUTION_COUNT" "$OUT_DIR/distributions/$distribution"
done

echo "Preparing one frozen stress/balanced performance corpus..."
PERF_DATA="$OUT_DIR/performance/stress-balanced-normalized.jsonl"
java -cp "$JAR" ru.itam.typing.realistic.RealisticWorkloadCli all \
  --count "$PERF_COUNT" --seed "$SEED" --noise stress --distribution balanced \
  --raw "$OUT_DIR/performance/stress-balanced-raw.jsonl" \
  --truth "$OUT_DIR/performance/stress-balanced-truth.jsonl" \
  --out "$PERF_DATA" > "$OUT_DIR/performance/generation.log"

for rules in 14 50 100 500; do
  if [ "$rules" = "14" ]; then
    RULE_FILE=rules/canonical-rules.yaml
  else
    RULE_FILE="$OUT_DIR/performance/rules-$rules.yaml"
    java -cp "$JAR" ru.itam.typing.realistic.RuleScaleCli --count "$rules" --out "$RULE_FILE" > /dev/null
  fi
  echo "Research benchmark: $rules rules..."
  java -cp "$JAR" ru.itam.typing.realistic.ResearchBenchmarkCli \
    --data "$PERF_DATA" --rules "$RULE_FILE" --warmup "$WARMUP" --runs "$RUNS" \
    --batch "$BATCH" --max "$PERF_COUNT" --out "$OUT_DIR/performance/benchmark-$rules.json" \
    > "$OUT_DIR/performance/benchmark-$rules.log"
done

if [ "$RUN_JMH" = "1" ]; then
  echo "Forked JMH cross-check (14/100/500 rules)..."
  java -jar "$JMH_JAR" 'ru.itam.typing.bench.EngineJmhBenchmark.*' \
    -rf json -rff "$OUT_DIR/performance/jmh.json" > "$OUT_DIR/performance/jmh.log"
fi

{
  echo "protocol=realistic-workload-v2"
  echo "sourceCommit=$SOURCE_COMMIT"
  echo "seed=$SEED"
  echo "accuracyCount=$ACCURACY_COUNT"
  echo "distributionCount=$DISTRIBUTION_COUNT"
  echo "performanceCount=$PERF_COUNT"
  echo "warmup=$WARMUP"
  echo "runs=$RUNS"
  echo "batch=$BATCH"
  echo "runJmh=$RUN_JMH"
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

find "$OUT_DIR" -type f ! -name SHA256SUMS.txt -print0 | sort -z | xargs -0 sha256sum > "$OUT_DIR/SHA256SUMS.txt"
python3 scripts/validate-research-v2.py "$OUT_DIR"
REVIEW_PACK=$(sh scripts/package-research-v2-review.sh "$OUT_DIR")
echo "REVIEW_PACK: $REVIEW_PACK"
echo "DONE: $OUT_DIR"
