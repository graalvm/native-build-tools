# E2E-maven-plugin-functional-tests: Maven functional tests exercise real Maven Native Image builds

Maven end-to-end coverage lives under `native-maven-plugin/src/functionalTest/`. These tests run
sample projects, generated projects, or issue reproducers through an isolated Maven executor,
seed a local Maven repository with plugin and support artifacts, and verify the behavior users see
from the Maven plugin. They provide executable evidence for §FS-maven-plugin, §AR-maven-plugin,
and the shared product contract in §root/FS-plugin-common-behavior.

## 1. Full local suite

Run the full Maven plugin functional suite locally with:

```bash
JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.12-graal ./gradlew :native-maven-plugin:functionalTest
```

This suite publishes the plugin and support artifacts to the local test repository, then runs Maven
sample or reproducer projects as external builds. That shape is intentional: it catches descriptor,
goal, lifecycle, repository, and command-line behavior that unit tests cannot see.

## 2. Single functional test class

Run one Maven functional test class with:

```bash
JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.12-graal ./gradlew \
  :native-maven-plugin:functionalTest \
  --tests org.graalvm.buildtools.maven.JavaApplicationFunctionalTest
```

For tests that require process isolation while debugging, use the documented `-DnoTestIsolation`
flag from the repository developer guide. Use Maven debug output from the functional test when the
goal configuration, lifecycle phase, or generated command line is the failure surface.

## 3. Scenario Coverage

| Scenario family | Evidence |
| --- | --- |
| Lifecycle native builds | `compile-no-fork` bound to `package` through native profiles |
| Direct goal usage | `native:compile`, `native:write-args-file`, and support goals |
| Native tests | `native:test`, skip flags, no-test behavior, runtime args, and launcher selection |
| Resources | main and test resource config generation |
| Reachability metadata | official/local metadata repositories, exclusions, forced versions, archives, and URLs |
| Tracing agent | `-Dagent=true`, direct/conditional/standard modes, merge, and metadata copy |
| Maven integration | shaded JARs, custom packaging, SBOM behavior, reproducers, and local repository seeding |

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

`test-native-maven-plugin.yml` runs generated Maven functional-test matrices and GraalVM dev-build
functional tests on pull requests. The CI workflow is specified by §root/CI-test-native-maven-plugin.

The CI matrix is the merge gate. Local runs should reproduce the failing class or reproducer first,
then broaden to the full suite before pushing behavior changes that affect goals, Maven parameter
binding, Native Image invocation, metadata, resources, SBOM behavior, or native tests.
