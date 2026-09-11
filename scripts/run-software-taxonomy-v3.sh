#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

if [ "${ALLOW_DIRTY:-0}" != "1" ] && [ -n "$(git status --porcelain)" ]; then
  echo "Refusing to run software taxonomy v3 on a dirty worktree. Commit/stash changes or set ALLOW_DIRTY=1." >&2
  exit 2
fi

SEED=${SEED:-20260916}
SOFTWARE_COUNT=${SOFTWARE_COUNT:-100000}
CONFLICT_WINDOW=${CONFLICT_WINDOW:-80}
STRICT_GATE=${STRICT_GATE:-1}
SOURCE_COMMIT=$(git rev-parse HEAD)
SHORT_COMMIT=$(printf '%.12s' "$SOURCE_COMMIT")
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT_DIR=${OUT_DIR:-results/software-taxonomy-v3/$SHORT_COMMIT-$STAMP}
CATALOG=${CATALOG:-data/software-catalog-v3.yaml}
RULES=${RULES:-rules/software-taxonomy-v3.yaml}
mkdir -p "$OUT_DIR/cases" "$OUT_DIR/provenance"

export JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-"-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8"}

echo "Building application..."
mvn -q clean package
JAR=target/itam-asset-typing-benchmark-2.0.0.jar

run_case() {
  name=$1
  noise=$2
  case_dir="$OUT_DIR/cases/$name"
  mkdir -p "$case_dir"
  raw="$case_dir/raw.jsonl"
  truth="$case_dir/truth.jsonl"
  train_raw="$case_dir/train-raw.jsonl"
  train_truth="$case_dir/train-truth.jsonl"
  hold_raw="$case_dir/holdout-raw.jsonl"
  hold_truth="$case_dir/holdout-truth.jsonl"
  normalized="$case_dir/holdout-normalized.jsonl"

  java -cp "$JAR" ru.itam.typing.realistic.SoftwareTaxonomyV3Generator \
    --catalog "$CATALOG" --count "$SOFTWARE_COUNT" --seed "$SEED" --noise "$noise" \
    --raw "$raw" --truth "$truth" --out "$case_dir/generation.json" > "$case_dir/generation.log"
  java -cp "$JAR" ru.itam.typing.realistic.HoldoutSplitCli \
    --raw "$raw" --truth "$truth" --train-raw "$train_raw" --train-truth "$train_truth" \
    --holdout-raw "$hold_raw" --holdout-truth "$hold_truth" --holdout-pct 20 \
    --out "$case_dir/split.json" > "$case_dir/split.log"
  java -cp "$JAR" ru.itam.typing.realistic.RealisticWorkloadCli materialize \
    --raw "$hold_raw" --truth "$hold_truth" --out "$normalized" > "$case_dir/materialize.log"
  java -cp "$JAR" ru.itam.typing.realistic.AccuracyEvaluationCli \
    --data "$normalized" --truth "$hold_truth" --rules "$RULES" \
    --conflict-window "$CONFLICT_WINDOW" --out "$case_dir/accuracy.json" > "$case_dir/accuracy.log"
}

echo "Generating paired RU-oriented software taxonomy scenarios..."
run_case clean clean
run_case light light
run_case stress stress
run_case severe severe

{
  echo "protocol=software-taxonomy-v3"
  echo "sourceCommit=$SOURCE_COMMIT"
  echo "seed=$SEED"
  echo "softwareCount=$SOFTWARE_COUNT"
  echo "catalog=$CATALOG"
  echo "rules=$RULES"
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

if [ "$STRICT_GATE" = "1" ]; then
  python3 scripts/summarize-software-taxonomy-v3.py "$OUT_DIR" --window "$CONFLICT_WINDOW" --strict
else
  python3 scripts/summarize-software-taxonomy-v3.py "$OUT_DIR" --window "$CONFLICT_WINDOW"
fi
find "$OUT_DIR" -type f ! -name SHA256SUMS.txt -print0 | sort -z | xargs -0 sha256sum > "$OUT_DIR/SHA256SUMS.txt"

echo "DONE: $OUT_DIR"
