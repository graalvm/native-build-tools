# CI-pull-request-ci: Pull request CI validates product, shared-library, sample, and spec changes

Native Build Tools pull request CI is split by ownership boundary so a change runs the cheapest
gate that can still prove the affected behavior. PR workflows use explicit `paths` filters and a
checked-out repository; product and spec gates use shared concurrency cancellation where repeated
runs should supersede older runs. Workflows that execute native-image or functional tests prepare
both a build JDK and a GraalVM test JDK through §CI-prepare-environment.

## 1. Pull request gates

| Workflow | Scope | Required evidence |
| --- | --- | --- |
| `check-grund-spec.yml` | Spec, code citation, sample, workflow, and build-logic changes that may affect grounded documentation. | The root workspace `grund check` run must resolve every declaration and citation across root, Gradle, and Maven namespaces; `grund fmt . --marker --check` must reject bare citation tokens. §CI-check-grund-spec |
| `macaron-check-github-actions.yml` | GitHub workflow and composite action changes. | Macaron's `check-github-actions` policy must validate workflow and composite action supply-chain rules for this repository package URL. §CI-macaron-check-github-actions |
| `test-native-gradle-plugin.yml` | Gradle plugin, samples, common modules, workflow/action changes, and shared version catalog changes. | Gradle functional tests, configuration-cache functional tests, unit tests, and inspections. §CI-test-native-gradle-plugin |
| `test-native-maven-plugin.yml` | Maven plugin, samples, common modules, workflow/action changes, and shared version catalog changes. | Maven functional tests plus GraalVM dev-build functional tests. §CI-test-native-maven-plugin |
| `test-graalvm-metadata.yml` | Reachability metadata common module and relevant workflow/action changes. | Checkstyle and unit tests for the metadata repository library. §CI-test-graalvm-metadata |
| `test-junit-platform-native.yml` | JUnit native support and relevant workflow/action changes. | Checkstyle, JVM tests, and native tests for `common/junit-platform-native`. §CI-test-junit-platform-native |

## 2. Publication workflows

| Workflow | Scope | Required evidence |
| --- | --- | --- |
| `deploy-documentation.yml` | Documentation website publication from `master` and manual dispatches. | Generated user documentation is built and pushed only from the canonical repository with publish credentials. §CI-deploy-documentation |
| `deploy-snapshots.yml` | Snapshot artifact publication after successful product and documentation workflows, or manual dispatch. | Snapshot publication runs only in the canonical repository after the configured upstream workflow completion events or an explicit manual run. §CI-deploy-snapshots |

## 3. Matrix generation

Gradle and Maven product workflows generate their functional-test matrices from the repository's
functional test list tasks before executing individual test classes. This lets CI shard expensive
functional tests while keeping local and CI test selection aligned with
§gradle/E2E-gradle-plugin-functional-tests and §maven/E2E-maven-plugin-functional-tests.

## 4. Dev-build coverage

Gradle and Maven product workflows include GraalVM dev-build jobs. These jobs run broad
functional-test coverage against a recent GraalVM build so Native Build Tools catches Native Image
integration changes before release users encounter them.

## 5. Artifact upload

Functional and module test workflows upload test reports on failure and success through
`actions/upload-artifact`. The reports are review evidence for failed PR gates and should come from
the module-specific build report directories.

# CI-check-grund-spec: Grund validation workflow

`check-grund-spec.yml` validates maintainer-facing specifications, component citations, Java
source citations, YAML workflow citations, and sample or build-logic references. It installs or
caches the configured `grund` binary, runs `grund check` at the repository root so the workspace
validates the root, Gradle, and Maven namespaces together, and runs
`grund fmt . --marker --check` so strict-mode bare ID-shaped tokens cannot silently bypass the
citation marker rule.

# CI-macaron-check-github-actions: GitHub Actions Macaron policy workflow

`macaron-check-github-actions.yml` validates `.github/workflows/**` and `.github/actions/**`
changes with the Macaron `check-github-actions` policy. The workflow checks out the repository and
runs the pinned `oracle/macaron` action with `policy_purl` set to the Native Build Tools package
URL pattern, so workflow and composite action changes are checked before merge. This gate is
documented for local contributors in `DEVELOPING.md`.

# CI-test-native-gradle-plugin: Gradle plugin PR workflow

`test-native-gradle-plugin.yml` validates the Gradle plugin through functional-test matrices,
configuration-cache functional-test matrices, unit tests, inspections, and a GraalVM dev-build
functional-test job. It is the PR gate for §gradle/FS-gradle-plugin and Gradle-facing end-to-end scenarios
in §gradle/E2E-gradle-plugin-functional-tests.

# CI-test-native-maven-plugin: Maven plugin PR workflow

`test-native-maven-plugin.yml` validates the Maven plugin through a generated functional-test
matrix and a GraalVM dev-build functional-test job. It is the PR gate for §maven/FS-maven-plugin and
Maven-facing end-to-end scenarios in §maven/E2E-maven-plugin-functional-tests.

# CI-test-graalvm-metadata: Reachability metadata library PR workflow

`test-graalvm-metadata.yml` validates `common/graalvm-reachability-metadata` with checkstyle and
unit tests. It protects the repository query and missing-metadata behavior specified by
§common/FS-common-libraries.5 and §common/FS-common-libraries.6.

# CI-test-junit-platform-native: JUnit native support PR workflow

`test-junit-platform-native.yml` validates `common/junit-platform-native` with checkstyle, JVM
tests, and native tests. It protects the shared native-test runtime behavior specified by
§FS-native-tests.3.

# CI-deploy-documentation: Documentation deployment workflow

`deploy-documentation.yml` regenerates and publishes the rendered documentation website from the
canonical `graalvm/native-build-tools` repository. It runs on pushes to `master` and manual
dispatches, prepares the build environment with push access through §CI-prepare-environment, and
publishes documentation by running `./gradlew :docs:gitPublishPush`. Documentation build behavior
is specified by §FS-build-infrastructure.3.

# CI-deploy-snapshots: Snapshot deployment workflow

`deploy-snapshots.yml` publishes development snapshots from the canonical
`graalvm/native-build-tools` repository. It runs after the documentation, Gradle plugin, Maven
plugin, or JUnit native workflows complete on `master`, and it also supports manual dispatches. It
prepares the build environment with push access through §CI-prepare-environment, then runs
`publishToMavenLocal publishAllPublicationsToSnapshotsRepository --no-parallel`. Snapshot
publication behavior is specified by §FS-build-infrastructure.5 and
§FS-build-infrastructure.5.1.

# CI-prepare-environment: Shared GitHub Action for CI Java and GraalVM setup

`.github/actions/prepare-environment/action.yml` installs the Gradle build JDK, optionally installs
a separate GraalVM used by functional and native tests, and optionally configures push access for
documentation or snapshot deployment workflows. PR test workflows should use this action instead
of duplicating Java and GraalVM setup.
