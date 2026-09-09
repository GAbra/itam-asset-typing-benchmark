# Prebuilt runtime path

Use this path when Docker Desktop works locally but Docker Hub access is blocked by the host/VPN proxy configuration.

1. Open the latest successful GitHub Actions `ci` run.
2. Download artifact `itam-typing-demo-prebuilt`.
3. Extract `itam-typing-demo-prebuilt.tar` into the repository root.
4. Load it locally:

```powershell
docker load -i .\itam-typing-demo-prebuilt.tar
```

5. Run the local benchmark without any Docker build or registry access:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-quick-demo-prebuilt.ps1
```

The script writes:

- `benchmark-results/verify-10000.json`
- `benchmark-results/benchmark-10000.json`
- `rules/generated/itam-typing.dmn`

The runtime image contains the already CI-built shaded application JAR and Java 21 JRE. Dataset generation and benchmark execution happen on the local PC.
