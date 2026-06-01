# AR-build-infrastructure: Build infrastructure stays outside product runtime behavior

Build infrastructure owns repository automation, generated support artifacts, documentation
publication, validation wiring, and release support. It may assemble, test, and publish product
modules, but product runtime code must not depend on infrastructure implementation classes.
Infrastructure behavior is specified by §FS-build-infrastructure.

## 1. Build logic ownership

`build-logic/` owns internal Gradle convention plugins and helper code for repository builds. It
may configure Java conventions, publication, documentation, functional testing, reachability
metadata module setup, generated version classes, and settings conventions.

Generated artifacts that product modules use at runtime must have explicit generation tasks and
stable inputs. Product modules consume generated outputs, not the build-logic implementation
classes that produce them.

## 2. Documentation architecture

The AsciiDoc tree under `docs/src/docs/asciidoc/` is the source for generated user documentation.
The Markdown tree under `docs/spec/` is the root maintainer-facing specification and citation
graph. The Gradle and Maven plugin modules also own workspace member specs under their local
`docs/` directories. Documentation build logic may copy snippets, render pages, and publish static
assets, but it must not make generated documentation a source of product runtime behavior.

## 3. CI and release boundaries

GitHub workflows under `.github/workflows/` and reusable actions under `.github/actions/` own
remote validation, dev-build checks, snapshot deployment, and release-sensitive publication
steps. Workflow behavior is specified by §CI-pull-request-ci, §CI-deploy-documentation, and
§CI-deploy-snapshots, and local execution equivalents are specified by
§gradle/E2E-gradle-plugin-functional-tests and §maven/E2E-maven-plugin-functional-tests.

Secrets, release credentials, and publication destinations belong to CI or release
configuration. Product modules expose publishable artifacts; infrastructure decides when and how
those artifacts are published.

## 4. Fixture and sample boundary

`samples/`, `test-support/`, plugin test fixtures, and Maven reproducers are evidence for product
behavior. They may depend on product artifacts under test and support artifacts published to a
local test repository, but they should not become general runtime libraries for product code.

The repository's samples and fixtures are part of the specification surface because the product
plugins are only useful when they work in real Gradle and Maven project shapes. This architecture
supports §GOAL-repository-fixtures-protect-real-build-scenarios and provides evidence for
§gradle/FS-gradle-plugin, §maven/FS-maven-plugin, §FS-common-libraries, and
§FS-plugin-common-behavior.3.

### 4.1 Fixture groups

Fixture ownership is split by the kind of scenario a test needs to run. `samples/` contains
example projects for Java applications, Java libraries, resources, reflection, tests, custom
source sets, Kotlin tests, multi-project builds, metadata repository integration, native config
integration, layered application behavior, integration tests, and non-native control cases.

`test-support/` contains reusable artifacts that product plugin functional tests can publish into
the common test repository. Gradle TestKit support, process helpers, result assertions, and
filesystem helpers live under `native-gradle-plugin/src/testFixtures/`.
`native-maven-plugin/reproducers/` contains Maven issue reproducers when a regression needs a
dedicated project shape that would add noise to shared samples.

### 4.2 Sample design

Samples should model realistic project shapes rather than minimal unit-test-only arrangements.
When a behavior exists in both Gradle and Maven, prefer a shared sample with both `build.gradle`
or `build.gradle.kts` and `pom.xml` where that keeps the scenario understandable.

Use build-tool-specific samples when the behavior is inherently tied to one build tool, such as
Gradle custom source sets or Maven assembly configuration. Samples should avoid unnecessary
network sensitivity. Functional test infrastructure may publish local support artifacts or use
repository-controlled test repositories when possible.

Samples used in end-to-end guides should stay close to documented user workflows. A sample change
that alters user-visible behavior should update both the specification and the corresponding
AsciiDoc guide when applicable.

### 4.3 Functional test roles

Functional tests prove that product plugin behavior works through real build-tool invocations.
Gradle functional tests use TestKit and should cover plugin application, native compile/run,
native tests, agent behavior, metadata repository integration, resource generation, custom source
sets, Kotlin scenarios, layered images, and option handling.

Maven functional tests should cover native compile/run behavior, native tests, agent behavior,
metadata repository integration, resource generation, SBOM behavior, issue reproducers, and
multi-module or packaging edge cases. Common module tests cover library behavior without invoking
full product plugin builds, including command-line utility, repository lookup, schema validation,
JUnit provider, and resource analyzer cases.

### 4.4 Scenario coverage

Each high-risk product behavior should have at least one executable scenario. The repository should
keep scenarios for simple application compilation, native executable execution, Java
library/shared-library output, main class configuration, Native Image option translation,
reflection metadata, resource metadata, tracing-agent metadata collection, metadata copy,
reachability metadata repository use, and missing metadata reporting where practical.

Native test scenarios should cover JUnit Platform native tests, application tests, multi-project
tests, custom test classes/resources, no-test handling, and compatibility-mode paths where
practical. Build-tool integration scenarios should cover Gradle configuration-cache-sensitive
wiring where practical, Maven lifecycle-bound goals, local repository seeding, and
build-tool-specific packaging behavior.

### 4.5 Fixture lifecycle

Add a new fixture when existing samples cannot express a behavior without becoming unclear or when
a regression needs a stable reproduction shape. Cite the most specific functional spec in the test
or fixture comment when code citations are added.

When product behavior changes, update fixtures in the same change as the spec and implementation
so the scenario continues to describe current behavior. Remove or consolidate fixtures only when
their behavior is covered elsewhere or the product no longer supports that scenario. Use
`grund refs` before renaming or deleting cited fixture docs or declarations.
