# Contributing

**English** · [Русский](CONTRIBUTING_RU.md)

Start with an issue for a new experimental axis or a change to classification semantics. Small fixes can go directly to a pull request.

## Local checks

Use Java 21 and Maven 3.9+ or Docker as described in the README:

```sh
mvn -B clean verify
python scripts/validate-results.py
python scripts/validate-baseline.py
python -m pip install -r scripts/requirements-figures.txt
python scripts/render-results.py --check
```

For engine/rule changes, run the generate → verify sequence from [the protocol](docs/TEST_PROTOCOL.md). Add focused cross-engine tests for changed semantics. All engines must execute the same canonical rules and use the shared resolver.

## Research contributions

Keep archived `benchmark-results/*.json` unchanged. Do not overwrite `benchmark-results/baseline-v2/` with a run from another machine or runtime. Put ad-hoc results in `results/local/`; submit new publishable experiments in a separately named directory with raw reports, exact commands, source commit, environment record and limitations.

Report correctness before performance. Include CPU model, RAM, OS, Java vendor/version, JVM flags, image ID/digest, JAR SHA-256, container CPU/memory limits, seed, input/rule hashes, warmup, batch size and every measured run. Do not upload environment variables, credentials, personal paths or production data.

Do not replace old numbers merely because a new machine is faster. Different environments are separate experiments, not optimization evidence.

## Documentation

Public documentation is maintained as English/Russian pairs. When changing a user-facing Markdown document, update its `_RU.md` counterpart in the same pull request when applicable and keep language-switch links working in both directions. Russian navigation should not unexpectedly drop the reader into an English-only page.

Be respectful, discuss evidence and trade-offs, and credit others' work. Contributions are made under the repository's MIT license; third-party material must retain its attribution and license.
