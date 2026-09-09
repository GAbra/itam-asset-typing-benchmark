# Contributing

Start with an issue for a new experimental axis or a change to classification semantics. Small fixes can go directly to a pull request.

## Local checks

Use Java 21 and Maven 3.9+ (or Docker as described in the README):

```sh
mvn -B clean verify
python scripts/validate-results.py
python -m pip install -r scripts/requirements-figures.txt
python scripts/render-results.py --check
```

For engine/rule changes, run the 10K generate → verify sequence from [the protocol](docs/TEST_PROTOCOL.md). Add focused cross-engine tests for changed semantics. All engines must execute the same canonical rules and use the shared resolver.

## Research contributions

Keep archived `benchmark-results/*.json` unchanged. Put local results in `results/local/`, then submit a named experiment directory with raw reports, exact commands, commit, environment and limitations. Do not replace old numbers with faster ones from a different machine.

Include CPU model, RAM, OS, Java vendor/version, JVM flags, container image ID and CPU/memory limits, seed and input SHA-256 hashes, warmup, batch size and all measured runs. Do not upload environment variables, credentials, personal paths or production data. Report correctness before performance; noisy CI hardware is unsuitable for throughput regression gates.

Use English for public documentation and keep the Russian README entry points aligned. Be respectful, discuss evidence and trade-offs, and credit others' work. Contributions are made under the repository's MIT license; third-party material must retain its attribution and license.
