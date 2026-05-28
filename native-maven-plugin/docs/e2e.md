# E2E-maven-plugin-functional-tests: Maven functional tests exercise real Maven Native Image builds

Maven end-to-end coverage lives under `native-maven-plugin/src/functionalTest/`. These tests run
sample projects, generated projects, or issue reproducers through an isolated Maven executor,
seed a local Maven repository with plugin and support artifacts, and verify the behavior users see
from the Maven plugin. They provide executable evidence for §FS-maven-plugin, §AR-maven-plugin,
and the shared product contract in §FS-plugin-common-behavior.

## 1. Full local suite

Run the full Maven plugin functional suite locally with:

```bash
./gradlew :native-maven-plugin:functionalTest
```

This suite is the broad local validation path for Maven plugin behavior, including lifecycle-bound
native builds, resource generation, metadata repository integration, agent workflows, native test
execution, SBOM behavior, and issue reproducers.

## 2. Single functional test class

Run one Maven functional test class with:

```bash
./gradlew :native-maven-plugin:functionalTest --tests org.graalvm.buildtools.maven.JavaApplicationFunctionalTest
```

For tests that require process isolation while debugging, use the documented `-DnoTestIsolation`
flag from the repository developer guide.

## 3. Local repository setup

Functional tests seed a local Maven repository before executing sample or reproducer builds. This
keeps tests independent from external publication and protects the Maven-specific architecture in
§AR-maven-plugin.6.

## 4. CI coverage

`test-native-maven-plugin.yml` runs generated Maven functional-test matrices and GraalVM dev-build
functional tests on pull requests. The CI workflow is specified by §CI-test-native-maven-plugin.
