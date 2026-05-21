# Native Build Tools Specification

This specification follows the component-doc pattern used by the referenced grund adoption: each
substantial component owns a `functional-spec.md` and/or `architecture.md` file. The folder gives
the component, while the ID kind gives the artifact type.

| File | Scope |
| --- | --- |
| §GRUND-001-native-build-tools-reason-for-existence | Project motivation |
| §GOAL-001-build-tool-native-image-workflows | Project goals |
| [repository/architecture.md](repository/architecture.md) | Repository structure and ownership |
| [gradle-plugin/functional-spec.md](gradle-plugin/functional-spec.md) | Gradle plugin behavior |
| [gradle-plugin/architecture.md](gradle-plugin/architecture.md) | Gradle plugin architecture |
| [maven-plugin/functional-spec.md](maven-plugin/functional-spec.md) | Maven plugin behavior |
| [maven-plugin/architecture.md](maven-plugin/architecture.md) | Maven plugin architecture |
| [common/functional-spec.md](common/functional-spec.md) | Shared metadata and resource behavior |
| [common/architecture.md](common/architecture.md) | Shared common library architecture |
| [testing/functional-spec.md](testing/functional-spec.md) | Native test behavior |
| [testing/architecture.md](testing/architecture.md) | Samples and fixtures architecture |
| [build-infra/architecture.md](build-infra/architecture.md) | Build, docs, release, and CI architecture |
| §DEC-001-adopt-component-functional-architecture-docs | Decisions |
| §RM-001-expand-spec-coverage-from-module-boundaries-to-feature-details | Roadmap |
