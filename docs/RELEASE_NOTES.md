# v1.0.0 release notes

**English** · [Русский](RELEASE_NOTES_RU.md)

First versioned release of the synthetic IT asset typing benchmark.

- Three real engines: HashMap + BitSet, compiled CEL and DMN / Apache KIE.
- Shared canonical rules, differential verification and targeted correctness tests.
- English/Russian documentation with language-preserving navigation.
- Explicit measurement boundaries and separate archived/controlled result sets.
- Controlled baseline v2 with exact runtime/environment provenance through 1M assets.
- Versioned JAR and portable Linux amd64 Docker image with SHA-256 checksums when the `v1.0.0` release workflow is triggered.

The historical baseline is preserved with its missing environment provenance clearly stated. The separately documented controlled experiment is stored in `benchmark-results/baseline-v2/` and must not be interpreted as a before/after optimization comparison with the archive.

Version 1.0.0 identifies a stable runnable research snapshot, not completion of every roadmap experiment or production readiness.

Planned versioned container after tag publication:

```text
ghcr.io/gabra/itam-asset-typing-benchmark:v1.0.0
```

`latest` is a moving convenience tag. Release assets include checksums and build provenance; validate downloads against `SHA256SUMS` and retain `SOURCE_COMMIT` / `IMAGE_ID` with research records.
