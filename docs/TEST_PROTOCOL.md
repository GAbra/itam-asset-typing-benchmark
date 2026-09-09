# Reproduction protocol

**English** · [Русский](TEST_PROTOCOL_RU.md)

Run commands from the repository root. Requirements: Docker with Compose/Linux containers, or Java 21 plus Maven 3.9+.

## Quick verification and measurement

Windows PowerShell:

```powershell
.\run-quick-demo.ps1
```

Linux/macOS/Git Bash:

```sh
sh run-quick-demo.sh
```

These entry points enforce build/test → generate → verify → benchmark → DMN export. A failed native command stops the sequence. Generated data is written under `data/generated/`; ad-hoc reports go to ignored `results/local/`.

Standalone `benchmark` does not imply correctness. Always run verification first when producing publishable measurements.

## Correctness gate

For a fully labeled generated dataset require:

```text
result = PASS
checked = requested asset count
groundTruthChecked = checked
engineMismatches = 0
groundTruthMismatches = 0
hashBitset = hashCel = hashDmn
```

An empty dataset fails verification. A mismatch or malformed input produces a nonzero process exit. If `--max` is used, only the requested prefix is classified; record that limit in the experiment note.

## Controlled baseline-v2 profile

The committed reference baseline uses the following fixed settings:

```text
seed = 20260909
warmup = 2
measured runs = 5
engine order = HASHMAP_BITSET -> CEL -> DMN_KIE
container CPUs = 4
container memory = 4 GiB
container memory+swap = 4 GiB
JVM = -Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4
```

Dataset/batch pairs:

| Assets | Batch |
|--:|--:|
| 100,000 | 5,000 |
| 500,000 | 10,000 |
| 1,000,000 | 10,000 |

The exact image ID and JAR checksum used by the committed run are stored in `benchmark-results/baseline-v2/environment.json`; the normalized command sequence is in `benchmark-results/baseline-v2/commands.txt`.

Do not silently substitute another image or JAR when claiming to reproduce that exact baseline. A run with another machine/runtime is a new experiment and should use a separately named result directory.

## Reproducing with Docker

For a new controlled experiment, build or load the runtime image, record its ID, then constrain Docker explicitly. Example shell pattern:

```sh
IMAGE=<image-or-digest>
REPO="$(pwd)"

docker run --rm \
  --cpus 4 \
  --memory 4g \
  --memory-swap 4g \
  -e 'JAVA_TOOL_OPTIONS=-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4' \
  -v "$REPO:/workspace" \
  -w /workspace \
  "$IMAGE" \
  generate --count 100000 --seed 20260909 --out data/generated/experiment-100000.jsonl
```

Then run `verify`, require `PASS`, and only then run `benchmark` with the intended batch size.

Git Bash on Windows may rewrite POSIX-looking Docker paths. If `/workspace` is converted unexpectedly, use the repository's existing Windows scripts or disable MSYS path conversion for the Docker invocation.

## Local Java

Build once:

```sh
mvn -B clean verify
```

Generate, verify and benchmark with the same seed/warmup/runs/batch values as the protocol. Record the full `java -version`, effective JVM flags, host CPU/RAM and source commit. A local-Java result is not directly comparable to the Docker baseline unless the execution environment is intentionally matched.

## Environment record

Before measuring, copy [ENVIRONMENT_TEMPLATE.md](ENVIRONMENT_TEMPLATE.md) into the experiment directory and fill it in. For Docker, record both host and container settings.

At minimum capture:

- source commit and worktree status;
- CPU model, cores/threads and physical RAM;
- host OS, Docker/virtualization version if applicable;
- Java vendor/full version, JVM flags, heap and GC;
- image ID/digest and JAR SHA-256;
- container CPU/memory limits;
- dataset count, seed, SHA-256 and generation sidecar;
- ruleset version/count/SHA-256;
- warmup, batch, measured passes and exact commands;
- verification report and all raw benchmark reports;
- background-load notes and known limitations.

Do not publish environment-variable dumps, credentials, private paths or production data.

## Validation of committed results

Archived baseline:

```sh
python scripts/validate-results.py
```

Controlled baseline v2:

```sh
python scripts/validate-baseline.py
```

Expected controlled-baseline output:

```text
PASS: baseline v2, 100,000 assets
PASS: baseline v2, 500,000 assets
PASS: baseline v2, 1,000,000 assets
```

Archived figures are checked with:

```sh
python -m pip install -r scripts/requirements-figures.txt
python scripts/render-results.py --check
```

CI runs correctness and packaging checks plus a smoke benchmark with no throughput threshold. CI runner speeds are not additions to the research baseline.

See [methodology](METHODOLOGY.md) for interpretation and [result-set structure](../benchmark-results/README.md) for why archived and controlled runs are kept separately.
