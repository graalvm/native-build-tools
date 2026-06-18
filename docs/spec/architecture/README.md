# Architecture

The architecture specs explain how Native Build Tools is put together, where each implementation
boundary lives, and how repository automation supports the Gradle and Maven product plugins.

Start with the repository map in [repository.md](repository.md) for component ownership,
dependency direction, and change flow ([§AR-repository-architecture](repository.md#ar-repository-architecture-native-build-tools-repository-architecture)). Use
[build-infrastructure.md](build-infrastructure.md) for build logic, documentation, release,
sample, fixture, and product/runtime boundaries ([§AR-build-infrastructure](build-infrastructure.md#ar-build-infrastructure-build-infrastructure-stays-outside-product-runtime-behavior)). Maintainer-facing
build behavior lives in [../functional/build-infrastructure.md](../functional/build-infrastructure.md)
([§FS-build-infrastructure](../functional/build-infrastructure.md#fs-build-infrastructure-build-documentation-and-release-infrastructure)). Pull request gates, publication workflows, shared actions, and
workflow-specific evidence live in [ci.md](ci.md) ([§AR-repository-ci](ci.md#ar-repository-ci-repository-ci-validates-publishes-and-supports-native-build-tools-automation)).
