# Archived baseline

These eight JSON reports were imported in initial commit `e16e158` and are retained unchanged. The performance reports contain five measured passes each for 10K, 100K, 500K and 1M synthetic assets. Verification reports contain zero engine and label mismatches.

The original CPU, RAM, OS, exact JVM, container limits and binary provenance were not captured. See [methodology](../docs/METHODOLOGY.md) for interpretation and limitations.

Run `python scripts/validate-results.py` to check counts, checksums within each run, summary arithmetic and verification consistency. This validates the reports internally; it does not rerun the historical measurements or certify their provenance.

Local scripts write to ignored `results/local/`. Submit new experiments in a separately named directory with their own environment and commands.
