# Portable runtime

Use this path if Docker is available but local registry/Maven access is blocked. You need a successful `ci` run on `main` with an unexpired artifact. Artifacts are retained for 14 days; an authenticated GitHub session may be needed to download them.

1. Open [CI runs](https://github.com/GAbra/itam-asset-typing-benchmark/actions/workflows/ci.yml) and choose a successful main-branch run.
2. Download `itam-asset-typing-benchmark-prebuilt` and extract it into the repository root.
3. Read `SOURCE_COMMIT` and use the matching source revision for a controlled experiment. `IMAGE_ID` identifies the packaged image.
4. Verify the tar SHA-256 against `SHA256SUMS`.
5. Load and run it:

```powershell
Get-FileHash .\itam-asset-typing-benchmark-prebuilt.tar -Algorithm SHA256
docker load -i .\itam-asset-typing-benchmark-prebuilt.tar
.\run-quick-demo-prebuilt.ps1
```

On Linux/macOS:

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

The image contains the CI-built shaded JAR, Java 21 JRE and canonical rules. The mounted repository supplies datasets and receives results; it also overrides the bundled rules at the same path. No build or registry access occurs after loading the image.

The PowerShell script also exports `rules/generated/itam-typing.dmn`. It checks native exit codes and stops on failed verification. Runtime execution does not rerun source tests.

The artifact targets Linux amd64. On other CPU architectures, emulation can distort measurements; build a native image for performance experiments.
