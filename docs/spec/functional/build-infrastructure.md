# FS-build-infrastructure: Build, documentation, and release infrastructure

Repository infrastructure exists to build, test, document, publish, and validate Native Build
Tools without becoming part of the product runtime API. It supports the repository architecture in
[§AR-repository-architecture](../architecture/repository.md#ar-repository-architecture-native-build-tools-repository-architecture), the plugin end-to-end test contracts in
[§gradle/E2E-functional-tests](../../../native-gradle-plugin/docs/e2e.md#e2e-functional-tests-gradle-functional-tests-exercise-real-gradle-native-image-builds) and [§maven/E2E-functional-tests](../../../native-maven-plugin/docs/e2e.md#e2e-functional-tests-maven-functional-tests-exercise-real-maven-native-image-builds), and the
pull request gates in [§AR-repository-ci](../architecture/ci.md#ar-repository-ci-repository-ci-validates-publishes-and-supports-native-build-tools-automation).

## At a Glance

| Maintainer need | Task or component | Output or effect |
| --- | --- | --- |
| Assemble included builds | `assemble` | delegated assembly across builds in the selected root mode |
| Validate included builds | `build`, `test`, `check`, `inspections` | delegated lifecycle work across builds in the selected root mode |
| Inspect publishable artifacts | `showPublications` | Maven coordinates printed from publishable modules |
| Publish locally for tests | `publishAllPublicationsToCommonRepository` | repository under `build/common-repo` |
| Prepare release archive | `releaseZip` | ZIP of common repository publication output |
| Update sample versions | `updateSamples`, `updateSamplesDir`, `updateMavenReprosDir` | rewritten sample/reproducer version placeholders |
| Generate runtime versions | `generateVersionInfo` | generated `org.graalvm.buildtools.VersionInfo` source |
| Package reachability metadata | `fetchMetadataRepository` | repository classifier artifact |
| Build documentation | `resolveJavadocs`, `asciidoctor`, `gitPublish*` | rendered docs and published documentation branch content |
| Generate CI matrix data | `dumpFunctionalTestList` | GitHub Actions matrix JSON |

## 1. Build orchestration

The root Gradle build coordinates product modules, shared modules, samples, documentation, and
internal convention plugins. Root and build-logic tasks may assemble, test, publish locally, and
prepare generated artifacts for the repository's modules. Aggregation tasks may coordinate
cross-module maintenance work, but product behavior must still live in product or common modules.

Root orchestration has two modes. Full mode is the default when
`org.graalvm.build.core` is absent or `false`; it includes every repository composite, including
documentation, and is the authoritative path for pull-request, release, and publication
validation. Core mode is selected only by `org.graalvm.build.core=true`; it includes product,
common, and build-logic composites while omitting the documentation composite before that build is
configured. Any other property value must fail settings evaluation rather than silently weakening
validation. Core mode is a local-feedback path that avoids unnecessary documentation configuration
and dependency resolution in support of [§GOAL-fast-feedback](../goals.md#goal-fast-feedback-native-build-workflows-provide-feedback-as-fast-as-practical).

### 1.1 Lifecycle aggregators

The root `assemble`, `test`, `check`, and `inspections` tasks are maintainer-facing shortcuts over
the builds included by the selected root mode. They must delegate to the matching task in each
included build so a maintainer can run one root command when building or validating repository-wide
changes. The root `build` task combines the selected mode's assembly and verification lifecycle;
in full mode it must additionally complete documentation rendering. These tasks are not product
API, and their task graph may change when included builds are added or removed.

### 1.2 Publication and release aggregators

The root `showPublications`, `publishToMavenLocal`,
`publishAllPublicationsToCommonRepository`, `publishAllPublicationsToSnapshotsRepository`,
`publishAllPublicationsToNexusRepository`, and `releaseZip` tasks are the maintainer-facing
publication surface. They must aggregate publishable included builds while excluding build-logic
and documentation builds that are not product artifacts. Repository-wide publication tasks must
fail early when invoked with parallel project execution because publication order and repository
mutation need a single coordinated writer.

`showPublications` reports the Maven coordinates that would be published. `releaseZip` assembles
the common repository output into a release archive without checksum side files. Snapshot and
Nexus publication tasks may depend on release or CI credentials, but those credentials must stay
outside product source code.

### 1.3 Sample and reproducer updates

The root `updateSamples`, `updateSamplesDir`, and `updateMavenReprosDir` tasks update version
placeholders in samples and Maven reproducers from the shared version catalog. They exist so
release preparation can update example projects consistently without manually editing each
sample. The tasks may rewrite `pom.xml` and `gradle.properties` files in the selected directories,
but must not change application source or product plugin behavior.

## 2. Build logic

`build-logic/` owns internal Gradle convention plugins and repository automation helpers. Common
build convention plugins own shared Java conventions, publishing conventions, documentation
configuration, functional testing configuration, reachability metadata module setup, utility
module generation, and settings conventions.

Reachability metadata fetching and module-generation infrastructure belongs in build logic when
it prepares repository artifacts or test inputs. Product plugins consume the resulting artifacts
through their normal dependencies.

### 2.1 Convention plugin behavior

Build-logic convention plugins must centralize repository-wide Gradle behavior that would otherwise
be duplicated in product and common module build files. This includes Java toolchain and Javadoc
settings, Maven publication repositories and POM metadata, shared version catalog lookup,
Checkstyle configuration, documentation rendering, Gradle functional-test wiring, and common local
repository exposure for composite builds.

The shared Java convention must configure every Javadoc task to run all doclint checks except the
missing-documentation category. Missing API comments and missing `@param`, `@return`, and `@throws`
tags must not produce warnings, while all other doclint categories remain enabled.

Ordinary repository test runs must report actionable failures without listing successful or
skipped tests or replaying routine test streams. Detailed test events and captured streams must
remain available through Gradle's normal diagnostic levels and generated test reports, refining
the concise-output goal in [§GOAL-concise-actionable-output](../goals.md#goal-concise-actionable-output-build-output-is-concise-actionable-and-token-efficient).

Root orchestration must resolve the required Java 17 compilation toolchain before delegating build
lifecycle work to included builds. If no matching installation is discoverable, it must fail with a
concise repository prerequisite diagnostic that identifies Java 17 and points maintainers to Gradle
toolchain discovery configuration. This check must use Gradle's toolchain service and must not
configure automatic JDK provisioning or replace the underlying resolution cause.

Convention plugins may add tasks and configurations to modules that apply them. Those additions
are maintainer-facing build behavior, not runtime behavior of the Native Build Tools plugins.

### 2.2 Generated version source

The utility-module convention must register `generateVersionInfo` when a module needs runtime
version constants. The task must generate `org.graalvm.buildtools.VersionInfo` from version
catalog values into a generated source directory and wire that directory into the module's main
source set. Runtime code may consume the generated class, but must not depend on build-logic
implementation classes.

### 2.3 Reachability metadata repository artifact

The reachability-module convention must register `fetchMetadataRepository`. The task downloads the
configured GraalVM Reachability Metadata repository archive, copies it into the module build
directory, and publishes it as the repository classifier artifact. Snapshot metadata versions must
not be reused from a stale task cache.

### 2.4 Functional-test matrix data

The GitHub Actions helper convention must register `dumpFunctionalTestList` for functional-test
source sets. The task must emit a JSON matrix containing operating systems, Java versions, Gradle
versions, configuration-cache versions where applicable, and discovered functional test class
names. In GitHub Actions it writes the matrix to `GITHUB_OUTPUT`; locally it may print the same
matrix for inspection.

## 3. Documentation

The AsciiDoc tree under `docs/src/docs/asciidoc/` remains the source for generated end-user
documentation. It documents how users apply and configure the Gradle and Maven plugins. The
Markdown tree under `docs/spec/` contains root grund declarations, goals, decision records,
shared component specs, CI specs, and architecture specs. Gradle and Maven plugin specs live
under `native-gradle-plugin/docs/` and `native-maven-plugin/docs/` as workspace member projects.
These maintainer-facing specs should be updated before behavior or design changes.

Documentation build logic must keep snippets, generated pages, static assets, and published
documentation output separate from product plugin runtime code.

### 3.1 Documentation generation and publication

Documentation build logic must resolve Javadoc artifacts, expand them into the generated
documentation tree, run AsciiDoc conversion, and publish rendered documentation to the configured
documentation branch. Release documentation should publish under the release version and refresh
the `latest` link; snapshot documentation must not replace the latest release pointer.

The documentation build's standard `build` lifecycle must run AsciiDoc conversion. Consequently,
the default full root `build` must render documentation, while core mode must neither configure the
documentation composite nor execute its rendering tasks. This lifecycle distinction does not alter
the Java 17 repository build floor or the ability to launch the checked-in wrapper with a supported
newer JDK as required by [§REQ-support-matrix.1](../requirements.md#1-declared-support).

## 4. Continuous integration

CI workflows are the repository's executable quality gates. The PR workflows are specified in
[§AR-repository-ci](../architecture/ci.md#ar-repository-ci-repository-ci-validates-publishes-and-supports-native-build-tools-automation) and cover Gradle plugin behavior, Maven plugin behavior, shared common
libraries, JUnit native support, reachability metadata behavior, end-to-end functional tests, and
spec citations.

Shared GitHub Actions setup should live in reusable actions or scripts, such as environment
preparation, so workflow differences describe product concerns rather than repeated boilerplate.

### 4.1 CI data generation

Build logic may generate CI data, such as functional-test matrices, when that data is derived from
the repository's source layout or version catalog. Generated CI data must stay reproducible from
the checked-out repository state so workflow behavior can be reviewed alongside code changes.

### 4.2 Generated issue-fix grounding

The repository-local GitHub issue-fix workflow must normalize and validate grund references from
the workspace root after implementation and after every review repair. For a configured grund
workspace it must run `grund fmt . --write --marker --cross-refs`, then require both `grund check`
and `grund fmt . --marker --cross-refs --check` to pass before focused validation and review.
Running the formatter against only an individual workspace member or changed file is insufficient
because cross-namespace Markdown targets depend on the root workspace configuration.

Formatting changes are part of the generated issue fix and must be reviewed with the rest of the
diff. A branch must not be published when either grounding command still reports unresolved
references or pending rewrites.

### 4.3 Generated issue-fix execution targets

The repository-local GitHub issue-fix workflow must expose its large-model and small-model agent
assignments as complete Rhei execution-target inputs. The defaults may select repository-preferred
agents, providers, models, and reasoning modes, but agent states must consume the rendered inputs
rather than repeat those selectors. Maintainers must therefore be able to replace either complete
target without editing the state machine.

An agent profile's autonomous approval and sandbox posture is independent from its reasoning-effort
modes. When the workflow configures an agent to run without approval prompts or sandbox restrictions,
selecting `high` or `xhigh` must change reasoning effort without weakening that autonomous posture.

## 5. Release and publication

Release infrastructure publishes Native Build Tools artifacts and documentation while keeping
module ownership clear. Gradle and Maven plugin artifacts are the externally visible deliverables.
Shared modules may be published only when they are part of the plugin dependency graph or a
documented support artifact.

Generated version classes and metadata should be produced by build logic so runtime code can read
stable version values without duplicating release rules. Snapshot deployment workflows may publish
development artifacts, but release-sensitive secrets and publication settings must remain in CI or
release infrastructure rather than product source code.

### 5.1 Snapshot publication helpers

Snapshot publication may use internal git helper tasks to clone the snapshot repository, reset it
to the expected baseline, copy the generated snapshot Maven repository, commit the changes, and
push the snapshot branch. These helper tasks are implementation details behind
`publishAllPublicationsToSnapshotsRepository`; maintainers should normally invoke the publication
task rather than each helper directly.

## 6. Infrastructure boundary

Infrastructure may depend on product modules to build, test, package, or publish them. Product
modules should not depend on infrastructure implementation classes except through Gradle build
scripts or generated artifacts intended for runtime use.

`samples/`, `test-support/`, plugin test fixtures, and Maven reproducers are evidence for product
behavior and are specified by [§AR-build-infrastructure.4](../architecture/build-infrastructure.md#4-fixture-and-sample-boundary). Spec files are not generated user
documentation; they are stable citation targets for maintainers, code comments, tests, CI
workflows, and future implementation work.
