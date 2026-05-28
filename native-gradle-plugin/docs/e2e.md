# E2E-gradle-plugin-functional-tests: Gradle functional tests exercise real Gradle Native Image builds

Gradle end-to-end coverage lives under `native-gradle-plugin/src/functionalTest/`. These tests run
sample or generated Gradle projects through Gradle TestKit, use the repository's local test
repository for plugin and support artifacts, and verify the behavior users see from the Gradle
plugin. They provide executable evidence for §FS-gradle-plugin, §AR-gradle-plugin, and the shared
product contract in §FS-plugin-common-behavior.

## 1. Full local suite

Run the full Gradle plugin functional suite locally with:

```bash
./gradlew :native-gradle-plugin:functionalTest
```

This suite is the broad local validation path for Gradle plugin behavior, including sample builds,
resource generation, metadata repository integration, agent collection, native test integration,
custom source sets, and Native Image option handling.

## 2. Single functional test class

Run one Gradle functional test class with:

```bash
./gradlew :native-gradle-plugin:functionalTest --tests org.graalvm.buildtools.gradle.JavaApplicationFunctionalTest
```

Use `-DgradleVersion=<version>` to run the suite against a specific Gradle version when
investigating version-matrix behavior.

## 3. Configuration-cache coverage

Configuration-cache functional tests validate that Gradle-specific task and provider wiring works
with Gradle's configuration-cache model. They protect §REQ-gradle-plugin-gradle-model-compatibility
and the architecture boundaries in §AR-gradle-plugin.2 and §AR-gradle-plugin.3.

## 4. CI coverage

`test-native-gradle-plugin.yml` runs generated Gradle functional-test matrices, configuration-cache
functional tests, unit tests, inspections, and GraalVM dev-build functional tests on pull
requests. The CI workflow is specified by §CI-test-native-gradle-plugin.
