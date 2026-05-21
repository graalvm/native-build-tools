# REPO-001-module-boundaries: The repository is divided into product plugins, shared libraries, fixtures, documentation, and build infrastructure

The repository should be specified as a composite workspace with these module groups:

| Module group | Paths | Responsibility |
| --- | --- | --- |
| Gradle product plugin | `native-gradle-plugin/` | Gradle plugin API, DSL, tasks, command-line providers, Gradle functional tests, and Gradle publication metadata. |
| Maven product plugin | `native-maven-plugin/` | Maven mojos, plugin descriptor generation, Maven configuration objects, Maven functional tests, SBOM behavior, and issue reproducers. |
| Shared libraries | `common/utils/`, `common/graalvm-reachability-metadata/`, `common/junit-platform-native/` | Build-tool-neutral support used by one or both product plugins. |
| Samples and fixtures | `samples/`, `test-support/`, plugin `src/functionalTest/`, plugin `src/testFixtures/`, `native-maven-plugin/reproducers/` | Realistic projects and reusable test artifacts that verify plugin behavior. |
| Documentation | `docs/`, `README.md`, `DEVELOPING.md` | User guides, changelog, developer guide, and this grund specification. |
| Build and CI infrastructure | `build-logic/`, `.github/`, `gradle/`, `config/`, `schemas/`, root Gradle files | Repository conventions, aggregation, publication, validation, schemas, and CI workflows. |

The product plugins are the externally visible deliverables. Shared libraries hold cross-cutting
Native Image behavior. Samples, fixtures, documentation, and infrastructure support development
and verification without becoming product API.

## Specification rule

New specification work should follow the component-doc pattern. Put behavior contracts in the
component's `functional-spec.md`; put implementation structure and ownership boundaries in the
component's `architecture.md`. Use `FS` IDs for functional behavior, `AR` IDs for architecture,
and keep the component visible in the slug and path.

The current component architecture specs are §AR-001-gradle-plugin-boundary,
§AR-002-maven-plugin-boundary, §AR-003-shared-common-libraries,
§AR-004-samples-and-functional-fixtures, and
§AR-005-build-documentation-and-release-infrastructure.
