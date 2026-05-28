# CI-pull-request-ci: Pull request CI validates product, shared-library, sample, and spec changes

Native Build Tools pull request CI is split by ownership boundary so a change runs the cheapest
gate that can still prove the affected behavior. Every PR workflow uses explicit `paths` filters,
shared concurrency cancellation, and a checked-out repository. Workflows that execute native-image
or functional tests prepare both a build JDK and a GraalVM test JDK through
§CI-prepare-environment.

## 1. Pull request gates

| Workflow | Scope | Required evidence |
| --- | --- | --- |
| `check-grund-spec.yml` | Spec, code citation, sample, workflow, and build-logic changes that may affect grounded documentation. | Root and plugin-local `grund check` runs must resolve every declaration and citation. §CI-check-grund-spec |
| `test-native-gradle-plugin.yml` | Gradle plugin, samples, common modules, workflow/action changes, and shared version catalog changes. | Gradle functional tests, configuration-cache functional tests, unit tests, and inspections. §CI-test-native-gradle-plugin |
| `test-native-maven-plugin.yml` | Maven plugin, samples, common modules, workflow/action changes, and shared version catalog changes. | Maven functional tests plus GraalVM dev-build functional tests. §CI-test-native-maven-plugin |
| `test-graalvm-metadata.yml` | Reachability metadata common module and relevant workflow/action changes. | Checkstyle and unit tests for the metadata repository library. §CI-test-graalvm-metadata |
| `test-junit-platform-native.yml` | JUnit native support and relevant workflow/action changes. | Checkstyle, JVM tests, and native tests for `common/junit-platform-native`. §CI-test-junit-platform-native |

## 2. Matrix generation

Gradle and Maven product workflows generate their functional-test matrices from the repository's
functional test list tasks before executing individual test classes. This lets CI shard expensive
functional tests while keeping local and CI test selection aligned with
§E2E-gradle-plugin-functional-tests and §E2E-maven-plugin-functional-tests.

## 3. Dev-build coverage

Gradle and Maven product workflows include GraalVM dev-build jobs. These jobs run broad
functional-test coverage against a recent GraalVM build so Native Build Tools catches Native Image
integration changes before release users encounter them.

## 4. Artifact upload

Functional and module test workflows upload test reports on failure and success through
`actions/upload-artifact`. The reports are review evidence for failed PR gates and should come from
the module-specific build report directories.

# CI-check-grund-spec: Grund validation workflow

`check-grund-spec.yml` validates maintainer-facing specifications, component citations, Java
source citations, YAML workflow citations, and sample or build-logic references. It installs or
caches the configured `grund` binary, runs `grund check` at the repository root, and runs
plugin-local checks in `native-gradle-plugin/` and `native-maven-plugin/` because those modules are
standalone grund projects.

# CI-test-native-gradle-plugin: Gradle plugin PR workflow

`test-native-gradle-plugin.yml` validates the Gradle plugin through functional-test matrices,
configuration-cache functional-test matrices, unit tests, inspections, and a GraalVM dev-build
functional-test job. It is the PR gate for §FS-gradle-plugin and Gradle-facing end-to-end scenarios
in §E2E-gradle-plugin-functional-tests.

# CI-test-native-maven-plugin: Maven plugin PR workflow

`test-native-maven-plugin.yml` validates the Maven plugin through a generated functional-test
matrix and a GraalVM dev-build functional-test job. It is the PR gate for §FS-maven-plugin and
Maven-facing end-to-end scenarios in §E2E-maven-plugin-functional-tests.

# CI-test-graalvm-metadata: Reachability metadata library PR workflow

`test-graalvm-metadata.yml` validates `common/graalvm-reachability-metadata` with checkstyle and
unit tests. It protects the repository query and missing-metadata behavior specified by
§FS-common-libraries.5 and §FS-common-libraries.6.

# CI-test-junit-platform-native: JUnit native support PR workflow

`test-junit-platform-native.yml` validates `common/junit-platform-native` with checkstyle, JVM
tests, and native tests. It protects the shared native-test runtime behavior specified by
§FS-native-tests-and-fixtures.3.

# CI-prepare-environment: Shared GitHub Action for CI Java and GraalVM setup

`.github/actions/prepare-environment/action.yml` installs the Gradle build JDK, optionally installs
a separate GraalVM used by functional and native tests, and optionally configures push access for
documentation or snapshot deployment workflows. PR test workflows should use this action instead
of duplicating Java and GraalVM setup.
