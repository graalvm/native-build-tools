# Architecture

The architecture specs explain how Native Build Tools is put together, where each implementation
boundary lives, and how repository automation supports the Gradle and Maven product plugins.

Start with the repository map in [repository.md](repository.md) for component ownership,
dependency direction, and change flow (§AR-repository-architecture). Use
[build-infrastructure.md](build-infrastructure.md) for build logic, documentation, release,
sample, fixture boundaries, and maintainer-facing build behavior (§AR-build-infrastructure,
§FS-build-infrastructure). Use [ci.md](ci.md) for pull request gates, publication workflows,
shared actions, and workflow-specific evidence (§CI-pull-request-ci).
