# TESTING-native-tests-and-fixtures: The plugins support compiling and running tests as native images

Native Build Tools must let supported Gradle and Maven projects compile test code into a native
image and execute that native test binary. This behavior depends on the shared
`junit-platform-native` module and is exposed through both product plugins. It realizes the test
portion of §GOAL-build-tool-native-image-workflows and is verified by
§GOAL-repository-fixtures-protect-real-build-scenarios.

## 1. Native test lifecycle

Native test support has two phases: collect enough JUnit Platform metadata while JVM tests run,
then build and run a native image that can execute the selected tests.

### 1.1 JVM-side test identifier collection

The build-tool plugin must arrange for JVM test execution to write JUnit Platform unique IDs to a
known output directory. The native test image build must consume that directory so the native
launcher knows which tests were selected.

### 1.2 Native image build

The native test image must include compiled test classes, test resources, application classes,
runtime dependencies, JUnit Platform dependencies, and the `junit-platform-native` support code.
The image name must be stable so later execution can find it.

### 1.3 Native test execution

After compilation, the build-tool plugin must execute the native test binary unless its
configuration explicitly skips execution. A failing native test process must fail the build in the
same way the corresponding build tool reports test failures.

### 1.4 Skip and no-test behavior

Gradle and Maven may expose different skip switches and task-selection paths, but the combined
workflow must distinguish disabling native test support, building a test image without running it
when the build tool supports that path, and handling projects where no tests were selected.

## 2. Test discovery and registration

Native Image cannot rely on all JVM reflection and resource discovery happening at runtime, so test
support must register test classes and platform configuration during image building.

### 2.1 Test class registration

The `junit-platform-native` feature must register test classes identified by the build-tool test
run so they are available to the native image runtime.

### 2.2 Unique ID input

The native launcher must accept the configured unique ID file prefix or discover default
locations. If no test IDs can be found when execution requires them, the launcher or build-tool
adapter must fail with an actionable error.

### 2.3 Nested and parameterized tests

Native test support must preserve JUnit Platform behavior needed by nested tests, method sources,
CSV sources, enum sources, converters, aggregators, class ordering, display-name generation, and
other supported Jupiter/Vintage scenarios represented by repository tests.

### 2.4 Test resources

Resources from test source sets or Maven test resource directories must be included in the native
test image when the corresponding JVM test would see them.

## 3. JUnit Platform support

The shared launcher and feature must adapt JUnit Platform behavior to Native Image constraints.

### 3.1 Native launcher

The Native Build Tools launcher must run only inside a native-image compiled test executable. It
must load selected test identifiers, create a JUnit Platform launcher request, execute tests, and
return a process result that build-tool plugins can treat as the native test outcome.

### 3.2 Native Image feature

The `JUnitPlatformFeature` must register the classes, resources, services, and runtime access
needed by supported JUnit Platform engines and Native Build Tools launcher code.

### 3.3 Config providers

The platform, Jupiter, and Vintage config providers must contribute Native Image metadata for
their supported JUnit components. Additional providers may be added when the repository supports
new JUnit Platform behavior.

### 3.4 Class initialization exclusions

When JUnit libraries ship Native Image class-initialization files that conflict with the native
test launcher path, the product plugins may add exclusion build arguments so the test image keeps
Native Build Tools' expected initialization behavior.

## 4. Build-tool adapters

Gradle and Maven adapters expose the same native test concept through different build-tool APIs.

### 4.1 Gradle adapter

Gradle must connect the `test` binary to the `test` source set and `test` task, build it with
`nativeTestCompile`, and execute it with `nativeTest`. Gradle-specific behavior is specified by
§GRADLE-plugin.6.

### 4.2 Maven adapter

Maven must expose native tests through `native:test`, use Maven test classes/resources and test
dependency scopes, and honor `skipTests`, `skipNativeTests`, `skipTestExecution`, and
`failNoTests`. Maven-specific behavior is specified by
§MAVEN-plugin.4.

### 4.3 Runtime arguments

Both adapters must allow runtime arguments to be passed to the native test executable. Runtime
arguments are distinct from Native Image build arguments and must not affect image generation.

## 5. Compatibility mode

Native Image compatibility mode changes native test execution because the build may require the
standard JUnit ConsoleLauncher path. The mode is defined in §GLOSS-compatibility-mode.

### 5.1 Detection

Build-tool adapters must detect compatibility mode from configured Native Image build arguments or
the Native Image options environment where the adapter has access to it.

### 5.2 Launcher selection

When compatibility mode is detected, the native test image may use JUnit's ConsoleLauncher instead
of `NativeImageJUnitLauncher`. In that mode, adapters must avoid adding Native Build Tools launcher
state that would conflict with the compatibility-mode execution path.

### 5.3 Non-compatibility mode

When compatibility mode is not detected, adapters must use `NativeImageJUnitLauncher` and
`JUnitPlatformFeature` so the shared native test behavior remains consistent across Gradle and
Maven.

## 6. Verification surface

The `common/junit-platform-native` module must contain JUnit-focused tests for launcher, feature,
registration, and provider behavior. Gradle and Maven functional test suites must include
application-with-tests, standalone JUnit tests, multi-project tests, Kotlin tests where supported,
custom source sets where supported, no-test behavior, and compatibility-mode coverage. Scenario
ownership is described by §TESTING-native-tests-and-fixtures.7.

## 7. Samples and fixtures

The repository's samples and fixtures are part of the specification surface because the product
plugins are only useful when they work in real Gradle and Maven project shapes. This architecture
supports §GOAL-repository-fixtures-protect-real-build-scenarios and provides evidence for
§GRADLE-plugin, §MAVEN-plugin,
§COMMON-libraries, and §TESTING-native-tests-and-fixtures.

### 7.1 Fixture groups

Fixture ownership is split by the kind of scenario a test needs to run. `samples/` contains
example projects for Java applications, Java libraries, resources, reflection, tests, custom
source sets, Kotlin tests, multi-project builds, metadata repository integration, native config
integration, layered application behavior, integration tests, and non-native control cases.

`test-support/` contains reusable artifacts that product plugin functional tests can publish into
the common test repository. Gradle TestKit support, process helpers, result assertions, and
filesystem helpers live under `native-gradle-plugin/src/testFixtures/`.
`native-maven-plugin/reproducers/` contains Maven issue reproducers when a regression needs a
dedicated project shape that would add noise to shared samples.

### 7.2 Sample design

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

### 7.3 Functional test roles

Functional tests prove that product plugin behavior works through real build-tool invocations.
Gradle functional tests use TestKit and should cover plugin application, native compile/run,
native tests, agent behavior, metadata repository integration, resource generation, custom source
sets, Kotlin scenarios, layered images, and option handling.

Maven functional tests should cover native compile/run behavior, native tests, agent behavior,
metadata repository integration, resource generation, SBOM behavior, issue reproducers, and
multi-module or packaging edge cases. Common module tests cover library behavior without invoking
full product plugin builds, including command-line utility, repository lookup, schema validation,
JUnit provider, and resource analyzer cases.

### 7.4 Scenario coverage

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

### 7.5 Fixture lifecycle

Add a new fixture when existing samples cannot express a behavior without becoming unclear or when
a regression needs a stable reproduction shape. Cite the most specific functional spec in the test
or fixture comment when code citations are added.

When product behavior changes, update fixtures in the same change as the spec and implementation
so the scenario continues to describe current behavior. Remove or consolidate fixtures only when
their behavior is covered elsewhere or the product no longer supports that scenario. Use
`grund refs` before renaming or deleting cited fixture docs or declarations.
