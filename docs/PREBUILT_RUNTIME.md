# Portable runtime

**English** · [Русский](PREBUILT_RUNTIME_RU.md)

## Current status

The Maven project version is `1.0.0`, and CI is prepared to publish a persistent GitHub Release and GHCR image when tag `v1.0.0` is created. Until that tag/release exists, use a successful main-branch CI artifact or build the runtime locally.

When the versioned release exists, prefer it over temporary Actions artifacts because release assets do not expire after 14 days.

Planned versioned container name:

```text
ghcr.io/gabra/itam-asset-typing-benchmark:v1.0.0
```

`latest` is a moving convenience alias and should not be used as the sole identifier of a research run.

## Temporary CI artifact

1. Open [CI runs](https://github.com/GAbra/itam-asset-typing-benchmark/actions/workflows/ci.yml) and choose a successful main-branch run.
2. Download `itam-asset-typing-benchmark-prebuilt`.
3. Extract `itam-asset-typing-benchmark-prebuilt.tar`, `SHA256SUMS`, `SOURCE_COMMIT`, `IMAGE_ID` and the JAR.
4. Verify the tar SHA-256 against `SHA256SUMS`.
5. Use `SOURCE_COMMIT` and `IMAGE_ID` as provenance for a controlled experiment.

Windows PowerShell:

```powershell
Get-FileHash .\itam-asset-typing-benchmark-prebuilt.tar -Algorithm SHA256
docker load -i .\itam-asset-typing-benchmark-prebuilt.tar
.\run-quick-demo-prebuilt.ps1
```

Linux/macOS:

```sh
sha256sum -c SHA256SUMS
docker load -i itam-asset-typing-benchmark-prebuilt.tar
docker run --rm -v "$PWD:/workspace" itam-asset-typing-benchmark:prebuilt generate --count 10000 --seed 20260909
docker run --rm -v "$PWD:/workspace" itam-asset-typing-benchmark:prebuilt verify --data data/generated/normalized-10000.jsonl --out results/local/verify-10000.json
```

After verification succeeds:

```sh
docker run --rm -v "$PWD:/workspace" itam-asset-typing-benchmark:prebuilt benchmark --data data/generated/normalized-10000.jsonl --warmup 2 --runs 5 --batch 2000 --out results/local/benchmark-10000.json
```

The image contains the CI-built shaded JAR, Java 21 JRE and canonical rules. The mounted repository supplies datasets and receives results. Runtime execution does not rerun source tests.

## Research use

For performance experiments, record the exact image ID/digest and JAR SHA-256. If reproducing baseline v2, also match the CPU/memory limits and JVM flags documented in [the protocol](TEST_PROTOCOL.md) and `benchmark-results/baseline-v2/environment.json`.

The packaged artifact targets Linux amd64. Emulation on another CPU architecture can distort performance measurements; build a native image for research comparisons.
