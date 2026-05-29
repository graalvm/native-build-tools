# Native Build Tools Specification

This directory is the grounded maintainer specification for Native Build Tools. It is not a user
guide replacement; it is the map that tells contributors and agents which behavior is owned where,
which commands or outputs prove it, and which ID to cite from code, tests, workflows, and future
docs.

The root namespace describes repository-wide contracts. The Gradle and Maven product plugins are
workspace members named `gradle` and `maven`, with local docs under
`native-gradle-plugin/docs/` and `native-maven-plugin/docs/`. Root docs cite product-plugin facts
with aliases such as `§gradle/FS-gradle-plugin` and `§maven/FS-maven-plugin`; plugin docs cite
repository-wide facts with `§root/<ID>`. Shared behavior, CI, build infrastructure, common
libraries, testing, decisions, and glossary terms stay in `docs/spec/`.

## Who should read what

| If you need to... | Start here | Then read |
| --- | --- | --- |
| understand what both product plugins promise | [plugin-common.md](plugin-common.md) | The Gradle and Maven functional specs |
| change Gradle DSL, task, or TestKit behavior | [../../native-gradle-plugin/docs/functional-spec.md](../../native-gradle-plugin/docs/functional-spec.md) | [../../native-gradle-plugin/docs/architecture.md](../../native-gradle-plugin/docs/architecture.md), [../../native-gradle-plugin/docs/e2e.md](../../native-gradle-plugin/docs/e2e.md) |
| change Maven goal, parameter, lifecycle, or reproducer behavior | [../../native-maven-plugin/docs/functional-spec.md](../../native-maven-plugin/docs/functional-spec.md) | [../../native-maven-plugin/docs/architecture.md](../../native-maven-plugin/docs/architecture.md), [../../native-maven-plugin/docs/e2e.md](../../native-maven-plugin/docs/e2e.md) |
| change shared resource, metadata, agent, or utility behavior | [common/functional-spec.md](common/functional-spec.md) | [common/architecture.md](common/architecture.md), [plugin-common.md](plugin-common.md) |
| change native test launcher, test image, sample, or fixture behavior | [testing/functional-spec.md](testing/functional-spec.md) | [testing/architecture.md](testing/architecture.md), plugin E2E docs |
| change build, publication, generated source, docs, or release tasks | [build-infra/functional-spec.md](build-infra/functional-spec.md) | [build-infra/architecture.md](build-infra/architecture.md), [ci.md](ci.md) |
| change pull request validation or GitHub Actions setup | [ci.md](ci.md) | [build-infra/functional-spec.md](build-infra/functional-spec.md) |
| decide whether something is in scope | [goals.md](goals.md), [non-goals.md](non-goals.md) | [requirements.md](requirements.md), [decisions.md](decisions.md) |

## Files at a glance

| File | Holds |
| --- | --- |
| [grund.md](grund.md) | Why Native Build Tools exists. |
| [goals.md](goals.md) | Product and repository outcomes. |
| [non-goals.md](non-goals.md) | Explicitly out-of-scope behavior. |
| [requirements.md](requirements.md) | Cross-cutting compatibility and support constraints. |
| [architecture.md](architecture.md) | Repository component map, dependency direction, and change flow. |
| [plugin-common.md](plugin-common.md) | Shared product behavior expected from both Gradle and Maven plugins. |
| [common/functional-spec.md](common/functional-spec.md) | Build-tool-neutral Native Image utilities, resources, metadata, and agent behavior. |
| [common/architecture.md](common/architecture.md) | Shared common-library module boundaries. |
| [testing/functional-spec.md](testing/functional-spec.md) | Native test lifecycle, JUnit support, adapters, and verification. |
| [testing/architecture.md](testing/architecture.md) | Samples, fixtures, and native test support architecture. |
| [build-infra/functional-spec.md](build-infra/functional-spec.md) | Maintainer task surface, generated artifacts, docs, publication, and CI data. |
| [build-infra/architecture.md](build-infra/architecture.md) | Build infrastructure ownership and product/runtime boundary. |
| [ci.md](ci.md) | Pull request workflows, shared actions, and workflow-specific gates. |
| [decisions.md](decisions.md) | Decisions and tradeoffs. |
| [roadmap.md](roadmap.md) | Planned work. |
| [glossary.md](glossary.md) | Domain terms used across specs. |

## Workspace Members

The product plugins are separate grund workspace members:

| Alias | Subproject | Purpose |
| --- | --- |
| `gradle` | [../../native-gradle-plugin/docs/grund.md](../../native-gradle-plugin/docs/grund.md) | Gradle plugin motivation, goals, functional spec, architecture, requirements, and E2E evidence. |
| `maven` | [../../native-maven-plugin/docs/grund.md](../../native-maven-plugin/docs/grund.md) | Maven plugin motivation, goals, functional spec, architecture, requirements, and E2E evidence. |

## Common workflows

| Workflow | Gradle entry point | Maven entry point | Shared spec |
| --- | --- | --- | --- |
| Build an application native image | `./gradlew nativeCompile` | `mvn -Pnative package` or `mvn -Pnative native:compile` | §FS-plugin-common-behavior.2 |
| Run the compiled application | `./gradlew nativeRun` | project-specific `exec:exec` or direct executable run | §FS-plugin-common-behavior.2 |
| Build and run native tests | `./gradlew nativeTest` | `mvn -Pnative native:test` or bound `test` phase | §FS-plugin-common-behavior.3 |
| Generate resource configuration | `./gradlew generateResourcesConfigFile` | `native:generateResourceConfig` | §FS-plugin-common-behavior.4 |
| Consume reachability metadata | `collectReachabilityMetadata`, then native compile | `native:add-reachability-metadata`, then native compile | §FS-plugin-common-behavior.4 |
| Collect/copy tracing-agent metadata | JVM task with `-Pagent`, then `metadataCopy` | `-Dagent=true`, then `native:metadata-copy` | §FS-plugin-common-behavior.5 |
| Inspect missing metadata | `./gradlew listLibrariesMissingMetadata` | `mvn -Pnative native:list-libraries-missing-metadata` | §FS-plugin-common-behavior.4 |

## Citation shape

Functional specs describe observable behavior: commands, DSL/XML, goals, task outputs, metadata
selection, generated files, native-image invocation, and verification expectations. Architecture
specs describe ownership, dependency direction, module boundaries, and implementation structure.

Use the most specific citation that supports the behavior. For example:

- Gradle task behavior from root docs: `§gradle/FS-gradle-plugin.2.1`
- Maven goal behavior from root docs: `§maven/FS-maven-plugin.1.1`
- cross-plugin parity: `§FS-plugin-common-behavior.4`
- shared resource analysis: `§FS-common-libraries.2`
- native test lifecycle: `§FS-native-tests-and-fixtures.1`
- build infrastructure task surface: `§FS-build-infrastructure.1.2`
- CI workflow behavior: `§CI-test-native-gradle-plugin`

Resolve cross-namespace citations with `grund <alias>/<ID>`, inspect a section map with
`grund <alias>/<ID> --toc`, and run `grund check` from the repository root before committing spec
or citation changes.
