# AR-repository-ci: Repository CI validates, publishes, and supports Native Build Tools automation

Native Build Tools repository CI is split by ownership boundary so pull request changes run the
cheapest gate that can still prove the affected behavior, while release-sensitive workflows publish
documentation and snapshots only from the canonical repository. PR workflows use explicit `paths`
filters and a checked-out repository; product and spec gates use shared concurrency cancellation
where repeated runs should supersede older runs. Workflows that execute native-image or functional
tests prepare both a build JDK and a GraalVM test JDK through [§AR-repository-ci.6](ci.md#6-shared-github-action-for-ci-java-and-graalvm-setup).

## 1. Pull request gates

| Workflow | Scope | Required evidence |
| --- | --- | --- |
| `check-grund-spec.yml` | Spec, code citation, sample, workflow, build-logic, and local hook changes that may affect grounded documentation. | The root workspace `grund check` run must resolve every declaration and citation across root, Gradle, and Maven namespaces; `grund fmt . --marker --cross-refs --check` must reject bare citation tokens and unlinked Markdown citations. [§AR-repository-ci.1.1](ci.md#11-grund-validation-workflow) |
| `macaron-check-github-actions.yml` | GitHub workflow and composite action changes. | Macaron's `check-github-actions` policy must validate workflow and composite action supply-chain rules for this repository package URL. [§AR-repository-ci.1.2](ci.md#12-github-actions-macaron-policy-workflow) |
| `test-native-gradle-plugin.yml` | Gradle plugin, samples, common modules, workflow/action changes, and shared version catalog changes. | Gradle functional tests, configuration-cache functional tests, unit tests, and inspections. [§AR-repository-ci.1.3](ci.md#13-gradle-plugin-pr-workflow) |
| `test-native-maven-plugin.yml` | Maven plugin, samples, common modules, workflow/action changes, and shared version catalog changes. | Maven functional tests, inspections, and GraalVM dev-build functional tests. [§AR-repository-ci.1.4](ci.md#14-maven-plugin-pr-workflow) |
| `test-graalvm-metadata.yml` | Reachability metadata common module and relevant workflow/action changes. | Checkstyle and unit tests for the metadata repository library. [§AR-repository-ci.1.5](ci.md#15-reachability-metadata-library-pr-workflow) |
| `test-junit-platform-native.yml` | JUnit native support and relevant workflow/action changes. | Checkstyle, JVM tests, and native tests for `common/junit-platform-native`. [§AR-repository-ci.1.6](ci.md#16-junit-native-support-pr-workflow) |

### 1.1 Grund validation workflow

`check-grund-spec.yml` validates maintainer-facing specifications, component citations, Java
source citations, YAML workflow citations, sample or build-logic references, and generated
Markdown cross-reference links. It installs or caches the configured `grund` binary, runs
`grund check` at the repository root so the workspace validates the root, Gradle, and Maven
namespaces together, and runs `grund fmt . --marker --cross-refs --check` so strict-mode bare
ID-shaped tokens cannot silently bypass the citation marker rule and Markdown citations remain
link-wrapped. The tracked pre-commit configuration mirrors these `grund` checks for local
commits.

### 1.2 GitHub Actions Macaron policy workflow

`macaron-check-github-actions.yml` validates `.github/workflows/**` and `.github/actions/**`
changes with the Macaron `check-github-actions` policy. The workflow checks out the repository and
runs the pinned `oracle/macaron` action with `policy_purl` set to the Native Build Tools package
URL pattern, so workflow and composite action changes are checked before merge. This gate is
documented for local contributors in `DEVELOPING.md`.

### 1.3 Gradle plugin PR workflow

`test-native-gradle-plugin.yml` validates the Gradle plugin through functional-test matrices,
configuration-cache functional-test matrices, unit tests, inspections, and a GraalVM dev-build
functional-test job. It is the PR gate for Gradle-facing end-to-end scenarios in
[§gradle/E2E-functional-tests](../../../native-gradle-plugin/docs/e2e.md#e2e-functional-tests-gradle-functional-tests-exercise-real-gradle-native-image-builds).

### 1.4 Maven plugin PR workflow

`test-native-maven-plugin.yml` validates the Maven plugin through a generated functional-test
matrix, inspections, and a GraalVM dev-build functional-test job. It is the PR gate for Maven-facing
end-to-end scenarios in [§maven/E2E-functional-tests](../../../native-maven-plugin/docs/e2e.md#e2e-functional-tests-maven-functional-tests-exercise-real-maven-native-image-builds).

### 1.5 Reachability metadata library PR workflow

`test-graalvm-metadata.yml` validates `common/graalvm-reachability-metadata` with checkstyle and
unit tests. It protects the repository query and missing-metadata behavior specified by
[§common/FS-common-libraries.5](../../../common/docs/functional-spec.md#5-reachability-metadata-repository) and [§common/FS-common-libraries.6](../../../common/docs/functional-spec.md#6-missing-metadata-reporting).

### 1.6 JUnit native support PR workflow

`test-junit-platform-native.yml` validates `common/junit-platform-native` with checkstyle, JVM
tests, and native tests. It protects the shared native-test runtime behavior specified by
[§FS-native-tests.3](../functional/native-tests.md#3-native-launcher-and-feature).

## 2. Publication workflows

| Workflow | Scope | Required evidence |
| --- | --- | --- |
| `deploy-documentation.yml` | Documentation website publication from `master` and manual dispatches. | Generated user documentation is built and pushed only from the canonical repository with publish credentials. [§AR-repository-ci.2.1](ci.md#21-documentation-deployment-workflow) |
| `deploy-snapshots.yml` | Snapshot artifact publication after successful product and documentation workflows, or manual dispatch. | Snapshot publication runs only in the canonical repository after the configured upstream workflow completion events or an explicit manual run. [§AR-repository-ci.2.2](ci.md#22-snapshot-deployment-workflow) |

### 2.1 Documentation deployment workflow

`deploy-documentation.yml` regenerates and publishes the rendered documentation website from the
canonical `graalvm/native-build-tools` repository. It runs on pushes to `master` and manual
dispatches, prepares the build environment with push access through [§AR-repository-ci.6](ci.md#6-shared-github-action-for-ci-java-and-graalvm-setup), and
publishes documentation by running `./gradlew :docs:gitPublishPush`. Documentation build behavior
is specified by [§FS-build-infrastructure.3](../functional/build-infrastructure.md#3-documentation).

### 2.2 Snapshot deployment workflow

`deploy-snapshots.yml` publishes development snapshots from the canonical
`graalvm/native-build-tools` repository. It runs after the documentation, Gradle plugin, Maven
plugin, or JUnit native workflows complete on `master`, and it also supports manual dispatches. It
prepares the build environment with push access through [§AR-repository-ci.6](ci.md#6-shared-github-action-for-ci-java-and-graalvm-setup), then runs
`publishToMavenLocal publishAllPublicationsToSnapshotsRepository --no-parallel`. Snapshot
publication behavior is specified by [§FS-build-infrastructure.5](../functional/build-infrastructure.md#5-release-and-publication) and
[§FS-build-infrastructure.5.1](../functional/build-infrastructure.md#51-snapshot-publication-helpers).

## 3. Matrix generation

Gradle and Maven product workflows generate their functional-test matrices from the repository's
functional test list tasks before executing individual test classes. This lets CI shard expensive
functional tests while keeping local and CI test selection aligned with
[§gradle/E2E-functional-tests](../../../native-gradle-plugin/docs/e2e.md#e2e-functional-tests-gradle-functional-tests-exercise-real-gradle-native-image-builds) and [§maven/E2E-functional-tests](../../../native-maven-plugin/docs/e2e.md#e2e-functional-tests-maven-functional-tests-exercise-real-maven-native-image-builds).

## 4. Dev-build coverage

Gradle and Maven product workflows include GraalVM dev-build jobs. These jobs run broad
functional-test coverage against a recent GraalVM build so Native Build Tools catches Native Image
integration changes before release users encounter them.

## 5. Artifact upload

Functional and module test workflows upload test reports on failure and success through
`actions/upload-artifact`. The reports are review evidence for failed PR gates and should come from
the module-specific build report directories.

## 6. Shared GitHub Action for CI Java and GraalVM setup

`.github/actions/prepare-environment/action.yml` installs the Gradle build JDK, optionally installs
a separate GraalVM used by functional and native tests, and optionally configures push access for
documentation or snapshot deployment workflows. PR test workflows should use this action instead
of duplicating Java and GraalVM setup.
