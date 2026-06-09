# E2E-functional-tests: Gradle functional tests exercise real Gradle Native Image builds

Gradle end-to-end coverage lives under `native-gradle-plugin/src/functionalTest/`. These tests run
sample or generated Gradle projects through Gradle TestKit, use the repository's local test
repository for plugin and support artifacts, and verify the behavior users see from the Gradle
plugin. They provide executable evidence for the focused Gradle functional specs under
`docs/functional/`, §AR-gradle-plugin, and the shared product contract in
§root/FS-plugin-common.

## 1. Full local suite

Run the full Gradle plugin functional suite locally with:

```bash
./gradlew :native-gradle-plugin:functionalTest
```

This is the broad local validation path for Gradle plugin behavior. It builds the plugin and
support artifacts into the repository's local test repository, then runs Gradle sample projects
through TestKit so task names, outputs, diagnostics, and Native Image invocation match real user
builds.

## 2. Single functional test class

Run one Gradle functional test class with:

```bash
./gradlew :native-gradle-plugin:functionalTest \
  --tests org.graalvm.buildtools.gradle.JavaApplicationFunctionalTest
```

Use `-DgradleVersion=<version>` to run the suite against a specific Gradle version when
investigating version-matrix behavior. Use `--info` when the Native Image command line or Gradle
task graph is the debugging surface.

## 3. Scenario Coverage

### 3.1 Java applications and libraries

`JavaApplicationFunctionalTest`, `JavaLibraryFunctionalTest`, and generated sample projects verify
that Gradle builds compile and run native applications and libraries. This protects
§FS-plugin-model, §FS-native-tasks, and §FS-native-invocation.

### 3.2 Native tests

`JavaApplicationWithTestsFunctionalTest`, `JUnitFunctionalTests`,
`KotlinApplicationWithTestsFunctionalTest`, and `MultiProjectJavaApplicationWithTestsFunctionalTest`
verify native test compilation, execution, JUnit support, Kotlin projects, and multi-project test
dependencies. This protects §FS-native-tests and §root/FS-native-tests.

### 3.3 Resources

`JavaApplicationWithResourcesFunctionalTest` verifies generated resource configuration for main and
test classpaths, explicit resource patterns, and resources already packaged with Native Image
configuration. This protects §FS-resources-metadata.1 and §FS-resources-metadata.2.

### 3.4 Reachability metadata

`ReachabilityMetadataFunctionalTest`, `NativeConfigRepoFunctionalTest`, and
`OfficialMetadataRepoFunctionalTest` verify official and local metadata repositories, exclusions,
forced versions, copied metadata, and missing metadata diagnostics. This protects
§FS-resources-metadata.3, §FS-resources-metadata.4, and §common/FS-common-libraries.5.

### 3.5 Tracing agent

`JavaApplicationWithAgentFunctionalTest` verifies `-Pagent`, instrumented JVM and test tasks,
standard and conditional mode behavior, agent output, configuration-cache compatibility, and
`metadataCopy`. This protects §FS-tracing-agent and §common/FS-common-libraries.3.

### 3.6 Native Image options

`NativeImageOptionsTest` and `LayeredApplicationFunctionalTest` verify build arguments, quick build,
image names, PGO/layer-related options, fat JAR behavior, runtime arguments, and command-line
construction. This protects §FS-native-tasks.4, §FS-native-invocation.3, and
§FS-native-invocation.5.

### 3.7 Gradle integration

Functional tests that exercise configuration cache, source sets, task dependencies, providers,
artifact transforms, and generated projects verify the Gradle model boundary. This protects
§REQ-gradle-model, §AR-gradle-plugin.2, and §AR-gradle-plugin.3.

When adding a behavior that a user can observe through a Gradle task, DSL option, generated file,
or Native Image invocation, add or update a functional test in the closest scenario family.

## 4. Configuration-cache coverage

Configuration-cache functional tests validate that Gradle-specific task and provider wiring works
with Gradle's configuration-cache model. They protect §REQ-gradle-model
and the architecture boundaries in §AR-gradle-plugin.2 and §AR-gradle-plugin.3.

Run the configuration-cache suite with the task exposed by the Gradle functional-testing
convention, or let CI run the generated matrix from §root/AR-repository-ci.1.3.

## 5. CI coverage

`test-native-gradle-plugin.yml` runs generated Gradle functional-test matrices, configuration-cache
functional tests, unit tests, inspections, and GraalVM dev-build functional tests on pull
requests. The CI workflow is specified by §root/AR-repository-ci.1.3.

The CI matrix is the merge gate. Local runs should reproduce the failing class or scenario first,
then broaden to the full suite before pushing behavior changes that affect task graph, Native
Image invocation, metadata, resources, or native tests.
