# Reproduction protocol

Run commands from the repository root. Requirements: Docker with Compose/Linux containers, or Java 21 plus Maven 3.9+. Docker builds need registry access and Maven Central; see [portable runtime](PREBUILT_RUNTIME.md) when those are unavailable.

## Quick verification and measurement

```powershell
.\run-quick-demo.ps1
```

Or:

```sh
sh run-quick-demo.sh
```

These entry points enforce build/test → generate → verify → benchmark → DMN export. A failed native command stops the sequence, including in Windows PowerShell 5.1. Generated data is in `data/generated/`; reports are in ignored `results/local/`.

For local Java, use the commands in [README](../README.md#local-java-21--maven-39). Run benchmark only after verify succeeds; standalone benchmark does not imply correctness.

## Correctness gate

For a fully labeled generated dataset, require:

```text
result = PASS
checked = requested asset count
groundTruthChecked = checked
engineMismatches = 0
groundTruthMismatches = 0
hashBitset = hashCel = hashDmn
```

An empty dataset fails verification. Exit code 2 indicates a verification failure; malformed input or invalid arguments also cause a nonzero exit. If `--max` is set, only the prefix is classified (the reader currently still scans the file); record that limit in the experiment.

## Scale the dataset

Build once using `scripts/build.ps1` or `sh scripts/build.sh`. Then in PowerShell:

```powershell
.\scripts\generate-large.ps1 -Count 100000
.\scripts\benchmark-size.ps1 -Count 100000
```

Repeat for `500000` and `1000000`. The benchmark script checks verification first and selects the archived protocol's batch sizes: 2K / 5K / 10K / 10K for 10K / 100K / 500K / 1M.

For other shells or the prebuilt image, use the same CLI with `--count` and `--batch` adjusted. Keep seed 20260909, warmup 2 and runs 5 for this protocol. Larger datasets require substantial disk space and processing time.

## Environment record

Before measuring, copy [ENVIRONMENT_TEMPLATE.md](ENVIRONMENT_TEMPLATE.md) into your experiment directory and fill it in. For Docker, record both host and container settings. New JSON automatically records Java/VM/OS properties, available processors, maximum JVM heap, GC names, input hashes and actual warmup size. This does not reveal host CPU model, physical RAM, Docker limits, JVM flags or background load.

Useful read-only commands include `git rev-parse HEAD`, `java -version`, `docker version`, `docker info` and `docker image inspect <image> --format '{{.Id}}'`. Extract only relevant non-sensitive fields into the published note. Do not upload a full environment-variable dump.

Generation emits a `.meta.json` sidecar with count, seed and profile distribution. Preserve it alongside raw reports. Archive exact command lines and input hashes with the source commit; keep each machine/configuration in a separate experiment directory.

## Report and figure checks

```sh
python scripts/validate-results.py
python -m pip install -r scripts/requirements-figures.txt
python scripts/render-results.py --check
```

These commands validate the committed baseline and detect stale figures. Run `python scripts/render-results.py` to regenerate figures after an intentional baseline/plot update.

CI runs correctness and packaging checks, plus a one-pass benchmark smoke test. It has no throughput threshold, and its smoke reports are not additions to the archived performance study. See [methodology](METHODOLOGY.md) for interpretation.
