# E2E-functional-test-suite: End-to-end functional tests exercise real Gradle and Maven builds

Native Build Tools end-to-end coverage is implemented as Gradle `functionalTest` source sets in
the product plugin modules. These tests run real sample or generated projects through Gradle
TestKit or an isolated Maven executor, publish support artifacts into a local test repository, and
verify the behavior users see from the build-tool plugins. They provide executable evidence for
§FS-repository-functional-spec.6 and are run by PR CI in §CI-test-native-gradle-plugin and
§CI-test-native-maven-plugin.

## 1. Gradle functional tests

Gradle end-to-end tests live under `native-gradle-plugin/src/functionalTest/`. The build logic in
`build-logic/gradle-functional-testing` creates the `functionalTest` source set, seeds a common
test repository, and can generate CI matrices with `:native-gradle-plugin:dumpFunctionalTestList`.

Run the full Gradle plugin functional suite locally with:

```bash
./gradlew :native-gradle-plugin:functionalTest
```

Run one Gradle functional test class with:

```bash
./gradlew :native-gradle-plugin:functionalTest --tests org.graalvm.buildtools.gradle.JavaApplicationFunctionalTest
```

Use `-DgradleVersion=<version>` to run the suite against a specific Gradle version when
investigating version-matrix behavior.

## 2. Maven functional tests

Maven end-to-end tests live under `native-maven-plugin/src/functionalTest/`. The Maven functional
test build plugin creates the `functionalTest` source set, seeds a local Maven repository with the
plugin and support artifacts, and can generate CI matrices with
`:native-maven-plugin:dumpFunctionalTestList`.

Run the full Maven plugin functional suite locally with:

```bash
./gradlew :native-maven-plugin:functionalTest
```

Run one Maven functional test class with:

```bash
./gradlew :native-maven-plugin:functionalTest --tests org.graalvm.buildtools.maven.JavaApplicationFunctionalTest
```

For tests that require process isolation while debugging, use the documented `-DnoTestIsolation`
flag from `DEVELOPING.md`.

## 3. Shared module native tests

The shared JUnit native runtime has its own module-level native test:

```bash
./gradlew :junit-platform-native:nativeTest
```

Reachability metadata repository behavior is tested with:

```bash
./gradlew :graalvm-reachability-metadata:test
```

These are not product-plugin end-to-end tests, but they are CI gates for common behavior that the
product plugin E2E suites consume.

## 4. Pull request CI coverage

`test-native-gradle-plugin.yml` and `test-native-maven-plugin.yml` run generated functional-test
matrices on pull requests. The Gradle workflow also runs configuration-cache functional tests.
Both product workflows include GraalVM dev-build lanes. The JUnit native and reachability metadata
workflows run the shared-module tests that underpin the product E2E suites.

## 5. Fixture expectations

E2E tests should prefer shared samples and reusable fixtures over one-off projects when an
existing scenario already captures the behavior. New fixtures should be added only when they make
a behavior clearer or preserve a regression shape that would be hard to express in an existing
sample. Fixture ownership is specified by §TESTING-native-tests-and-fixtures.7.
