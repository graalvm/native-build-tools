# E2E-maven-plugin-functional-tests: Maven functional tests exercise real Maven Native Image builds

Maven end-to-end coverage lives under `native-maven-plugin/src/functionalTest/`. These tests run
sample projects, generated projects, or issue reproducers through an isolated Maven executor,
seed a local Maven repository with plugin and support artifacts, and verify the behavior users see
from the Maven plugin. They provide executable evidence for the focused Maven functional specs
under `docs/functional/`, §AR-maven-plugin, and the shared product contract in
§root/FS-plugin-common-behavior.

## 1. Full local suite

Run the full Maven plugin functional suite locally with:

```bash
./gradlew :native-maven-plugin:functionalTest
```

This suite publishes the plugin and support artifacts to the local test repository, then runs Maven
sample or reproducer projects as external builds. That shape is intentional: it catches descriptor,
goal, lifecycle, repository, and command-line behavior that unit tests cannot see.

## 2. Single functional test class

Run one Maven functional test class with:

```bash
./gradlew :native-maven-plugin:functionalTest \
  --tests org.graalvm.buildtools.maven.JavaApplicationFunctionalTest
```

For tests that require process isolation while debugging, use the documented `-DnoTestIsolation`
flag from the repository developer guide. Use Maven debug output from the functional test when the
goal configuration, lifecycle phase, or generated command line is the failure surface.

## 3. Scenario Coverage

### 3.1 Lifecycle native builds

`JavaApplicationFunctionalTest`, `JavaLibraryFunctionalTest`, and profile-based sample builds verify
`compile-no-fork` bound to lifecycle phases such as `package`. This protects
§FS-maven-goal-surface.1, §FS-maven-goal-surface.4, and §FS-maven-native-image-builds.

### 3.2 Direct goal usage

`JavaApplicationFunctionalTest` and related functional tests verify direct goal usage such as
`native:compile`, `native:write-args-file`, and support goals. This protects
§FS-maven-goal-surface.1, §FS-maven-goal-surface.3, and §FS-maven-native-image-builds.8.

### 3.3 Native tests

`JavaApplicationWithTestsFunctionalTest`, `MavenTestExecutionFunctionalTests`,
`JUnitFunctionalTests`, and `issues/ModuleWithoutSourcesFunctionalTest` verify `native:test`, skip
flags, no-test behavior, runtime arguments, launcher selection, and reactor modules without source
artifacts. This protects §FS-maven-goal-surface.2, §FS-maven-native-tests, and
§root/FS-native-tests.

### 3.4 Resources

`JavaApplicationWithResourcesFunctionalTest` verifies main and test resource configuration
generation and resource propagation into native builds. This protects §FS-maven-native-image-builds.4 and
§FS-maven-resources-and-metadata.1.

### 3.5 Reachability metadata

`MetadataRepositoryFunctionalTest`, `OfficialMetadataRepositoryFunctionalTest`, and
`issues/ExcludeDependenciesFunctionalTest` with `reproducers/issue-612` verify official and local
metadata repositories, exclusions, forced versions, archives, URLs, and missing metadata reports.
This protects §FS-maven-resources-and-metadata.2, §FS-maven-resources-and-metadata.3, and §common/FS-common-libraries.5.

### 3.6 Tracing agent

`JavaApplicationWithAgentFunctionalTest` verifies `-Dagent=true`, standard/direct/conditional modes,
disabled stages, merge behavior, and `native:metadata-copy`. This protects §FS-maven-tracing-agent and
§common/FS-common-libraries.3.

### 3.7 Maven integration

`SBOMFunctionalTest`, `JavaApplicationWithTestsFunctionalTest`, `issues/JavaAppWithTestsAndParentPomFunctionalTest`
with `reproducers/issue-144`, and `issues/ModuleWithoutSourcesFunctionalTest` with
`reproducers/issue-727` verify shaded JARs, custom packaging, SBOM behavior, parent POM merging,
issue reproducers, and local repository seeding. This protects §FS-maven-native-image-builds.6,
§FS-maven-configuration-model.3, and §AR-maven-plugin.6.

When adding behavior that a user can observe through a Maven goal, plugin parameter, generated
file, lifecycle binding, or Native Image invocation, add or update a functional test in the
closest scenario family.

## 4. Local repository setup

Functional tests seed a local Maven repository before executing sample or reproducer builds. This
keeps tests independent from external publication and protects the Maven-specific architecture in
§AR-maven-plugin.6.

The local repository is part of the test contract: tests should resolve the plugin exactly as a
sample project would, rather than reaching into compiled classes directly.

## 5. CI coverage

`test-native-maven-plugin.yml` runs generated Maven functional-test matrices, inspections, and
GraalVM dev-build functional tests on pull requests. The CI workflow is specified by
§root/AR-repository-ci.1.4.

The CI matrix is the merge gate. Local runs should reproduce the failing class or reproducer first,
then broaden to the full suite before pushing behavior changes that affect goals, Maven parameter
binding, Native Image invocation, metadata, resources, SBOM behavior, or native tests.
