# AR-native-tests-and-fixtures: Samples and fixtures verify real Gradle and Maven project shapes

The repository's samples and fixtures are part of the specification surface because the product
plugins are only useful when they work in real Gradle and Maven project shapes. This architecture
supports §GOAL-repository-fixtures-protect-real-build-scenarios and provides evidence for
§gradle/FS-gradle-plugin, §maven/FS-maven-plugin, §FS-common-libraries, and
§FS-native-tests-and-fixtures.

## 1. Fixture groups

Fixture ownership is split by the kind of scenario a test needs to run. `samples/` contains
example projects for Java applications, Java libraries, resources, reflection, tests, custom
source sets, Kotlin tests, multi-project builds, metadata repository integration, native config
integration, layered application behavior, integration tests, and non-native control cases.

`test-support/` contains reusable artifacts that product plugin functional tests can publish into
the common test repository. Gradle TestKit support, process helpers, result assertions, and
filesystem helpers live under `native-gradle-plugin/src/testFixtures/`.
`native-maven-plugin/reproducers/` contains Maven issue reproducers when a regression needs a
dedicated project shape that would add noise to shared samples.

## 2. Sample design

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

## 3. Functional test roles

Functional tests prove that product plugin behavior works through real build-tool invocations.
Gradle functional tests use TestKit and should cover plugin application, native compile/run,
native tests, agent behavior, metadata repository integration, resource generation, custom source
sets, Kotlin scenarios, layered images, and option handling.

Maven functional tests should cover native compile/run behavior, native tests, agent behavior,
metadata repository integration, resource generation, SBOM behavior, issue reproducers, and
multi-module or packaging edge cases. Common module tests cover library behavior without invoking
full product plugin builds, including command-line utility, repository lookup, schema validation,
JUnit provider, and resource analyzer cases.

## 4. Scenario coverage

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

## 5. Fixture lifecycle

Add a new fixture when existing samples cannot express a behavior without becoming unclear or when
a regression needs a stable reproduction shape. Cite the most specific functional spec in the test
or fixture comment when code citations are added.

When product behavior changes, update fixtures in the same change as the spec and implementation
so the scenario continues to describe current behavior. Remove or consolidate fixtures only when
their behavior is covered elsewhere or the product no longer supports that scenario. Use
`grund refs` before renaming or deleting cited fixture docs or declarations.
