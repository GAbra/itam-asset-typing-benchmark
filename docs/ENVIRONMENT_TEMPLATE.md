# Experiment environment

**English** · [Русский](ENVIRONMENT_TEMPLATE_RU.md)

Copy this template into each new experiment directory. Record unknown values as `unknown`; do not infer them later.

- Experiment ID / UTC start and completion time:
- Source commit and dirty-worktree status:
- Measurement script commit, if different:
- CPU model / physical cores / logical processors:
- Physical RAM:
- Host OS and version/build:
- Java vendor / full version / JVM:
- JVM flags / heap limits / garbage collector:
- Docker/Desktop/Engine version, if used:
- Image ID or digest / architecture:
- JAR SHA-256:
- Container CPU limit / memory limit / memory+swap:
- Host virtualization / WSL version, if applicable:
- Power mode / background load / other running workloads:
- Dataset count / seed / SHA-256 / generation sidecar:
- Ruleset version / enabled rule count / SHA-256:
- Engine order:
- Warmup iterations / warmup records:
- Measured passes / batch size / max limit:
- Exact commands:
- Verification report / ground-truth coverage:
- Raw benchmark reports for every run:
- Notes, interruptions/resume details and limitations:

Keep different machines/configurations in separate experiment directories. Do not publish usernames, private paths, environment-variable dumps, credentials, secrets or production data.
