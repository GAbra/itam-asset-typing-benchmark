First versioned release of the synthetic IT asset typing benchmark.

- Three real engines: HashMap + BitSet, compiled CEL and DMN / Apache KIE.
- Shared canonical rules, differential verification and targeted correctness tests.
- English/Russian documentation, explicit measurement boundaries and archived results.
- Versioned JAR and portable Linux amd64 Docker image with SHA-256 checksums.

The historical baseline is preserved with its missing environment provenance clearly stated.
See the repository's `benchmark-results/baseline-v2/` for the separately documented new experiment.
Version 1.0.0 identifies a stable runnable research snapshot, not completion of every roadmap experiment or production readiness.

Container: `ghcr.io/gabra/itam-asset-typing-benchmark:v1.0.0` (`latest` is a moving convenience tag).
The portable tar attached here is a persistent alternative that does not require registry access.
After loading it, the local image name is `itam-asset-typing-benchmark:prebuilt`.
Validate downloads against `SHA256SUMS`; `SOURCE_COMMIT` and `IMAGE_ID` identify the build.
