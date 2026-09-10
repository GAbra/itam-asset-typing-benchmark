#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <results-dir>" >&2
  exit 2
fi

ROOT_DIR=$1
if [ ! -d "$ROOT_DIR" ]; then
  echo "Results directory not found: $ROOT_DIR" >&2
  exit 2
fi

ROOT_DIR=$(CDPATH= cd -- "$ROOT_DIR" && pwd)
LIST_FILE="$ROOT_DIR/.review-files.txt"
BUNDLE="$ROOT_DIR/review-pack.tar.gz"

rm -f "$LIST_FILE" "$BUNDLE"

(
  cd "$ROOT_DIR"
  find accuracy distributions -type f \( \
    -name 'accuracy.json' -o \
    -name 'split.json' -o \
    -name '*.meta.json' \
  \) -print 2>/dev/null || true
  find performance -maxdepth 1 -type f \( \
    -name 'benchmark-*.json' -o \
    -name 'jmh.json' -o \
    -name 'generation.log' -o \
    -name 'rules-*.yaml' \
  \) -print 2>/dev/null || true
  find provenance -type f -print 2>/dev/null || true
  test -f SHA256SUMS.txt && printf '%s\n' SHA256SUMS.txt
) | LC_ALL=C sort -u > "$LIST_FILE"

if [ ! -s "$LIST_FILE" ]; then
  echo "No review artifacts found under $ROOT_DIR" >&2
  rm -f "$LIST_FILE"
  exit 2
fi

tar -czf "$BUNDLE" -C "$ROOT_DIR" -T "$LIST_FILE"
rm -f "$LIST_FILE"

printf '%s\n' "$BUNDLE"
