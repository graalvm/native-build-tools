# Architecture

The architecture specs explain how Native Build Tools is put together, where each implementation
boundary lives, and how repository automation supports the Gradle and Maven product plugins.

Start with the repository map in [repository.md](repository.md) for component ownership,
dependency direction, and change flow (§AR-repository-architecture). Use
[build-infrastructure.md](build-infrastructure.md) for build logic, documentation, release,
sample, fixture, and product/runtime boundaries (§AR-build-infrastructure). Maintainer-facing
build behavior lives in [../functional/build-infrastructure.md](../functional/build-infrastructure.md)
(§FS-build-infrastructure). Pull request gates, publication workflows, shared actions, and
workflow-specific evidence live in [ci.md](ci.md) (§AR-pull-request-ci).
