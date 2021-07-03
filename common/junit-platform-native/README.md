# JUnit Platform support for GraalVM Native Image
Feature that enables JUnit Platform testing support for GraalVM Native Image
![](https://github.com/graalvm/native-image-configuration/actions/workflows/junit-platform-native-feature.yml/badge.svg)

## Usage
There are two main modes of operation using this feature:
1. Out of the box (for tests which do not use reflection internally).
2. With the agent run (for tests with internal reflection).

Either way, artifact produced by building this repository should be included on classpath (either by using build tool plugins - as was done in [samples subdirectory](../../samples), or by adding manual configuration).

If support for your build system is missing at the moment, adding:
```bash
native-image
    ...
    --no-fallback
    --features=org.graalvm.junit.platform.JUnitPlatformFeature
    org.graalvm.junit.platform.NativeImageJUnitLauncher
```
to your `native-image` invocation should be sufficient.

If tests require reflection, then [`native-image-agent`](https://docs.oracle.com/en/graalvm/enterprise/19/guide/reference/native-image/tracing-agent.html) run is necessary.
For Gradle users this should be as easy as running tests using:
```bash
./gradlew -Pagent testConsoleLauncher
./gradlew -Pagent testNative
```

For more information refer to `project.hasProperty("agent")` sections in [native-image-testing.gradle](gradle/native-image-testing.gradle).

### Important note:
In order for feature to register required tests, you either need to run JUnit Platform test on JVM with this feature on the classpath BEFORE `native-image` invocation, or run `native-image` with `-DtestDiscovery` argument added.

## Building
GraalVM with `native-image` should be present on the system, as well as `$GRAALVM_HOME` environment variable pointing to its location.
```bash
./gradlew publishToMavenLocal
```
This will publish the latest artifact to local maven repository.

*You can also take a look at CI workflow [here](../../.github/workflows/junit-platform-native-feature.yml).*

## Testing
Following tasks are present in this project for testing this feature:
```bash
./gradlew testConsoleLauncher # runs standard JUnit test using JVM and ConsoleLauncher
./gradlew -Pagent testConsoleLauncher # includes agent and generates required reflection configuration
./gradlew testNative # builds native image using this feature for configuration
./gradlew -Pagent testNative # builds native image using additional configuration from agent run
```
