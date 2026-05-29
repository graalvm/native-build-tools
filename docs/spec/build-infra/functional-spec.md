# FS-build-infrastructure: Build, documentation, and release infrastructure

Repository infrastructure exists to build, test, document, publish, and validate Native Build
Tools without becoming part of the product runtime API. It supports the repository architecture in
§AR-repository-architecture, the plugin end-to-end test contracts in
§E2E-gradle-plugin-functional-tests and §E2E-maven-plugin-functional-tests, and the pull request
gates in §CI-pull-request-ci.

## 1. Build orchestration

The root Gradle build coordinates product modules, shared modules, samples, documentation, and
internal convention plugins. Root and build-logic tasks may assemble, test, publish locally, and
prepare generated artifacts for the repository's modules. Aggregation tasks may coordinate
cross-module maintenance work, but product behavior must still live in product or common modules.

### 1.1 Verification aggregators

The root `test`, `check`, and `inspections` tasks are maintainer-facing shortcuts over the
included builds. They must delegate to the matching task in each included build so a maintainer can
run one root command when validating repository-wide changes. They are not product API, and their
task graph may change when included builds are added or removed.

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
Markdown tree under `docs/spec/` contains root grund declarations, goals, decisions, roadmap
items, shared component specs, CI specs, and architecture specs. Gradle and Maven plugin specs live
under `native-gradle-plugin/docs/` and `native-maven-plugin/docs/` as standalone grund projects.
These maintainer-facing specs should be updated before behavior or design changes.

Documentation build logic must keep snippets, generated pages, static assets, and published
documentation output separate from product plugin runtime code.

### 3.1 Documentation generation and publication

Documentation build logic must resolve Javadoc artifacts, expand them into the generated
documentation tree, run AsciiDoc conversion, and publish rendered documentation to the configured
documentation branch. Release documentation should publish under the release version and refresh
the `latest` link; snapshot documentation must not replace the latest release pointer.

## 4. Continuous integration

CI workflows are the repository's executable quality gates. The PR workflows are specified in
§CI-pull-request-ci and cover Gradle plugin behavior, Maven plugin behavior, shared common
libraries, JUnit native support, reachability metadata behavior, end-to-end functional tests, and
spec citations.

Shared GitHub Actions setup should live in reusable actions or scripts, such as environment
preparation, so workflow differences describe product concerns rather than repeated boilerplate.

### 4.1 CI data generation

Build logic may generate CI data, such as functional-test matrices, when that data is derived from
the repository's source layout or version catalog. Generated CI data must stay reproducible from
the checked-out repository state so workflow behavior can be reviewed alongside code changes.

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
behavior and are specified by §FS-native-tests-and-fixtures. Spec files are not generated
user documentation; they are stable citation targets for maintainers, code comments, tests, CI
workflows, and future implementation work.
