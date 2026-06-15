# FS-native-tests: Gradle tasks compile and run native JUnit tests

Gradle native tests let users keep their normal JUnit test source set and ask the plugin to build
and execute those tests as a native image. Runtime semantics are defined by
[§root/FS-native-tests](../../../docs/spec/functional/native-tests.md#fs-native-tests-both-plugins-compile-and-execute-junit-tests-as-a-native-image).

## 1. Test task integration

The default `test` binary must derive its classes, resources, classpath, test identifiers, and JUnit
support from the Gradle `test` source set and `test` task. A normal Gradle JUnit setup should be
enough for `nativeTest`.

```groovy
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

test {
    useJUnitPlatform()
}
```

### 1.1 Custom test binaries

A custom test binary registered with `registerTestBinary` must derive its classes, resources,
runtime classpath, test identifiers, and JUnit native support from the source set and `Test` task
selected by `usingSourceSet` and `forTestTask`. A custom source set must not have to inherit from
the default `test` source set configurations to build or run as a native test image.

## 2. Native test execution

`nativeTestCompile` builds the native test image. `nativeTest` runs that image unless the test
support DSL disables native testing or the requested task graph only builds the test image. Native
test failures must fail the Gradle build in the same way Java test failures do.

## 3. Compatibility mode

When Native Image compatibility mode is detected, Gradle native test behavior may use the original
JUnit ConsoleLauncher path rather than the Native Build Tools launcher path, as described by
[§root/FS-native-tests.5](../../../docs/spec/functional/native-tests.md#5-compatibility-mode).

## 4. Native test command examples

`nativeTestCompile` is the image-generation entry point; `nativeTest` is the CI entry point because
it compiles the test image, executes it, and reports failures through Gradle.

```bash
./gradlew nativeTestCompile
./gradlew nativeTest
```
