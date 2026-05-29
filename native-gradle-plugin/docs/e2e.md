# E2E-gradle-plugin-functional-tests: Gradle functional tests exercise real Gradle Native Image builds

Gradle end-to-end coverage lives under `native-gradle-plugin/src/functionalTest/`. These tests run
sample or generated Gradle projects through Gradle TestKit, use the repository's local test
repository for plugin and support artifacts, and verify the behavior users see from the Gradle
plugin. They provide executable evidence for §FS-gradle-plugin, §AR-gradle-plugin, and the shared
product contract in §FS-plugin-common-behavior.

## 1. Full local suite

Run the full Gradle plugin functional suite locally with:

```bash
JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.12-graal ./gradlew :native-gradle-plugin:functionalTest
```

This is the broad local validation path for Gradle plugin behavior. It builds the plugin and
support artifacts into the repository's local test repository, then runs Gradle sample projects
through TestKit so task names, outputs, diagnostics, and Native Image invocation match real user
builds.

## 2. Single functional test class

Run one Gradle functional test class with:

```bash
JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.12-graal ./gradlew \
  :native-gradle-plugin:functionalTest \
  --tests org.graalvm.buildtools.gradle.JavaApplicationFunctionalTest
```

Use `-DgradleVersion=<version>` to run the suite against a specific Gradle version when
investigating version-matrix behavior. Use `--info` when the Native Image command line or Gradle
task graph is the debugging surface.

## 3. Scenario Coverage

| Scenario family | Evidence |
| --- | --- |
| Java applications and libraries | sample builds compile and run native images |
| Native tests | application-with-tests, JUnit, Kotlin, and multi-project test scenarios |
| Resources | generated resource configuration and test resources |
| Reachability metadata | repository lookup, exclusions, official/local metadata, and missing metadata reports |
| Tracing agent | `-Pagent`, instrumented JVM tasks, agent output, and `metadataCopy` |
| Native Image options | build args, quick build, image name, PGO, fat JAR, layers, and runtime args |
| Gradle integration | configuration cache, source sets, task dependencies, providers, and artifact transforms |

When adding a behavior that a user can observe through a Gradle task, DSL option, generated file,
or Native Image invocation, add or update a functional test in the closest scenario family.

## 4. Configuration-cache coverage

Configuration-cache functional tests validate that Gradle-specific task and provider wiring works
with Gradle's configuration-cache model. They protect §REQ-gradle-plugin-gradle-model-compatibility
and the architecture boundaries in §AR-gradle-plugin.2 and §AR-gradle-plugin.3.

Run the configuration-cache suite with the task exposed by the Gradle functional-testing
convention, or let CI run the generated matrix from §CI-test-native-gradle-plugin.

## 5. CI coverage

`test-native-gradle-plugin.yml` runs generated Gradle functional-test matrices, configuration-cache
functional tests, unit tests, inspections, and GraalVM dev-build functional tests on pull
requests. The CI workflow is specified by §CI-test-native-gradle-plugin.

The CI matrix is the merge gate. Local runs should reproduce the failing class or scenario first,
then broaden to the full suite before pushing behavior changes that affect task graph, Native
Image invocation, metadata, resources, or native tests.
