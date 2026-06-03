# Native Build Tools Specification

This directory is the grounded maintainer specification for Native Build Tools. It is not a user
guide replacement; it is the map that tells contributors and agents which behavior is owned where,
which commands or outputs prove it, and which ID to cite from code, tests, workflows, and future
docs.

The root namespace describes repository-wide contracts. The common libraries and product plugins
are workspace members named `common`, `gradle`, and `maven`, with local docs under `common/docs/`,
`native-gradle-plugin/docs/`, and `native-maven-plugin/docs/`. Root docs cite workspace-member
facts with aliases such as `§common/FS-common-libraries`, `§gradle/FS-gradle-plugin`, and
`§maven/FS-maven-plugin`; member docs cite repository-wide facts with `§root/<ID>`. Shared plugin
behavior, CI, build infrastructure, native-test behavior, decisions, and glossary terms stay in
`docs/spec/`.

## Who should read what

| If you need to... | Start here | Then read |
| --- | --- | --- |
| understand what both product plugins promise | [functional/plugin-common.md](functional/plugin-common.md) | The Gradle and Maven functional specs |
| change Gradle DSL, task, or TestKit behavior | [../../native-gradle-plugin/docs/functional-spec.md](../../native-gradle-plugin/docs/functional-spec.md) | [../../native-gradle-plugin/docs/architecture.md](../../native-gradle-plugin/docs/architecture.md), [../../native-gradle-plugin/docs/e2e.md](../../native-gradle-plugin/docs/e2e.md) |
| change Maven goal, parameter, lifecycle, or reproducer behavior | [../../native-maven-plugin/docs/functional-spec.md](../../native-maven-plugin/docs/functional-spec.md) | [../../native-maven-plugin/docs/architecture.md](../../native-maven-plugin/docs/architecture.md), [../../native-maven-plugin/docs/e2e.md](../../native-maven-plugin/docs/e2e.md) |
| change shared resource, metadata, agent, or utility behavior | [../../common/docs/functional-spec.md](../../common/docs/functional-spec.md) | [../../common/docs/architecture.md](../../common/docs/architecture.md), [functional/plugin-common.md](functional/plugin-common.md) |
| change native test launcher or test image behavior | [functional/native-tests.md](functional/native-tests.md) | [../../common/docs/functional-spec.md](../../common/docs/functional-spec.md), plugin E2E docs |
| change sample, fixture, or reproducer behavior | [architecture/build-infrastructure.md](architecture/build-infrastructure.md) | plugin E2E docs |
| change build, publication, generated source, docs, or release tasks | [functional/build-infrastructure.md](functional/build-infrastructure.md) | [architecture/build-infrastructure.md](architecture/build-infrastructure.md), [architecture/ci.md](architecture/ci.md) |
| change pull request validation or GitHub Actions setup | [architecture/ci.md](architecture/ci.md) | [functional/build-infrastructure.md](functional/build-infrastructure.md), [architecture/build-infrastructure.md](architecture/build-infrastructure.md) |
| decide whether something is in scope | [goals.md](goals.md), [non-goals.md](non-goals.md) | [requirements.md](requirements.md), [decisions/README.md](decisions/README.md) |

## Files at a glance

| File | Holds |
| --- | --- |
| [grund.md](grund.md) | Why Native Build Tools exists. |
| [goals.md](goals.md) | Product and repository outcomes. |
| [non-goals.md](non-goals.md) | Explicitly out-of-scope behavior. |
| [requirements.md](requirements.md) | Cross-cutting compatibility and support constraints. |
| [architecture/README.md](architecture/README.md) | Architecture overview and links to architecture sections. |
| [architecture/repository.md](architecture/repository.md) | Repository component map, dependency direction, and change flow. |
| [functional/README.md](functional/README.md) | Functional-spec overview and links to functional sections. |
| [functional/plugin-common.md](functional/plugin-common.md) | Parity boundary for behavior shared by both plugins; links to the focused contracts below. |
| [functional/native-image-builds.md](functional/native-image-builds.md) | Shared contract for building native images from project state. |
| [functional/native-tests.md](functional/native-tests.md) | Shared contract for compiling and running JUnit tests as a native image. |
| [functional/resources-and-metadata.md](functional/resources-and-metadata.md) | Shared contract for resource config, reachability metadata, missing-metadata reports, dynamic access metadata, and schema validation. |
| [functional/tracing-agent.md](functional/tracing-agent.md) | Shared contract for Native Image tracing-agent attachment and post-processing. |
| [functional/option-precedence.md](functional/option-precedence.md) | Shared contract for command-line vs durable configuration precedence. |
| [functional/build-infrastructure.md](functional/build-infrastructure.md) | Build, documentation, release, generated artifact, and maintainer task behavior. |
| [architecture/build-infrastructure.md](architecture/build-infrastructure.md) | Build infrastructure ownership, product/runtime boundary, samples, fixtures, and reproducers. |
| [architecture/ci.md](architecture/ci.md) | Pull request workflows, shared actions, and workflow-specific gates. |
| [decisions/README.md](decisions/README.md) | Decisions and tradeoffs. |
| [glossary.md](glossary.md) | Domain terms used across specs. |

## Workspace Members

The common libraries and product plugins are separate grund workspace members:

| Alias | Subproject | Purpose |
| --- | --- |
| `common` | [../../common/docs/grund.md](../../common/docs/grund.md) | Common library motivation, goals, requirements, functional spec, architecture, and executable evidence. |
| `gradle` | [../../native-gradle-plugin/docs/grund.md](../../native-gradle-plugin/docs/grund.md) | Gradle plugin motivation, goals, functional spec, architecture, requirements, and E2E evidence. |
| `maven` | [../../native-maven-plugin/docs/grund.md](../../native-maven-plugin/docs/grund.md) | Maven plugin motivation, goals, functional spec, architecture, requirements, and E2E evidence. |

## Common workflows

| Workflow | Gradle entry point | Maven entry point | Shared spec |
| --- | --- | --- | --- |
| Build an application native image | `./gradlew nativeCompile` | `mvn -Pnative package` or `mvn -Pnative native:compile` | §FS-native-image-builds |
| Run the compiled application | `./gradlew nativeRun` | project-specific `exec:exec` or direct executable run | §FS-native-image-builds |
| Build and run native tests | `./gradlew nativeTest` | `mvn -Pnative native:test` or bound `test` phase | §FS-native-tests |
| Generate resource configuration | `./gradlew generateResourcesConfigFile` | `native:generateResourceConfig` | §FS-resources-and-metadata.1 |
| Consume reachability metadata | `collectReachabilityMetadata`, then native compile | `native:add-reachability-metadata`, then native compile | §FS-resources-and-metadata.2 |
| Collect/copy tracing-agent metadata | JVM task with `-Pagent`, then `metadataCopy` | `-Dagent=true`, then `native:metadata-copy` | §FS-tracing-agent-workflows |
| Inspect missing metadata | `./gradlew listLibrariesMissingMetadata` | `mvn -Pnative native:list-libraries-missing-metadata` | §FS-resources-and-metadata.3 |

## Citation shape

Functional specs describe observable behavior: commands, DSL/XML, goals, task outputs, metadata
selection, generated files, native-image invocation, and verification expectations. Architecture
specs describe ownership, dependency direction, module boundaries, and implementation structure.

Use the most specific citation that supports the behavior. For example:

- Gradle task behavior from root docs: `§gradle/FS-gradle-plugin.2.1`
- Maven goal behavior from root docs: `§maven/FS-maven-plugin.1.1`
- cross-plugin parity: `§FS-plugin-common-behavior`
- shared resource analysis: `§common/FS-common-libraries.2`
- native test lifecycle: `§FS-native-tests.1`
- build infrastructure task surface: `§FS-build-infrastructure.1.2`
- CI workflow architecture: `§AR-repository-ci.1.3`

Resolve cross-namespace citations with `grund <alias>/<ID>`, inspect a section map with
`grund <alias>/<ID> --toc`, and run `grund check` from the repository root before committing spec
or citation changes.

Use explicit `§<ID>` or `§alias/<ID>` citations everywhere. Java Checkstyle allows the `§` marker as
the only non-ASCII citation exception, so source comments should use the same marked citation shape
as Markdown, YAML, and workflow files. Bare ID-shaped tokens are ignored because `[reference] strict =
true`.
