# Native Image Gradle Plugin
Gradle plugin for GraalVM Native Image building
![](https://github.com/graalvm/native-image-build-tools/actions/workflows/native-image-gradle-plugin.yml/badge.svg)

## Usage
Add following to `plugins` section of your project's `build.gradle`:
```groovy
plugins {
    // ...

    // Apply GraalVM Native Image plugin
    id 'native-image-gradle-plugin' version "${insert_plugin_version}"
}
```
### DSL definition
This plugin is primarily configured using Gradle DSL. This can be achieved by adding `nativeImage` and `testNative` sections to your `build.gradle` like:
```groovy
nativeImage {
  imageName = "application"
  main = "org.test.Main" // Main class
  args("--no-server") // Arguments to be passed to native-image invocation
  debug = false
  verbose = false
  fallback = false
  classpath("dir1", "dir2") // Adds "dir1" and "dir2" to the classpath
  jvmArgs("flag") // Passes 'flag' directly to the JVM running the native image builder
  systemProperties = [name1: 'value1', name2: 'value2'] // Sets a system property
}

import TestMode

testNative {
  mode = TestMode.TEST_LISTENER // or TestMode.DISCOVERY ('-DtestDiscovery' on command line)
  agent = false // Can be also set on command line using '-Pagent'
  persistConfig = false // Used in conjunction with 'agent' to save its output to META-INF
  //...
  // all of the options from 'nativeImage' block are supported here except changing main class name
}
```

> :information_source: All Gradle Groovy DSL build file `build.gradle` task examples also work for the Gradle Kotlin DSL build file `build.gradle.kts` when placed inside the `tasks` container e.g.
> ```kotlin
> tasks {
>     nativeImage {
>         args("--static")
>     }
> }
> ```

> :information_source: Also note that for options that can be set using command-line, if both DSL and command-line options are present, command-line options take precedence.

### Avalilable tasks:
```
Application tasks
-----------------
runNative - Runs this project as a native-image application

Build tasks
-----------
buildNative - Builds native-image from this project.

Verification tasks
------------------
testNative - If necessary builds and runs native-image compiled tests.
testNativeBuild - Builds native image with tests.

```

### Reflection support
If your project requires reflection, then [`native-image-agent`](https://docs.oracle.com/en/graalvm/enterprise/19/guide/reference/native-image/tracing-agent.html) run might be necessary.
This should be as easy as appending `-Pagent` to `run` and `buildNative`, or`test` and `testNative` task invocations:
```bash
./gradlew -Pagent run # Runs on JVM with native-image-agent.
./gradlew -Pagent buildNative # Builds image using configuration acquired by agent.

# For testing
./gradlew -Pagent test # Runs on JVM with native-image-agent.
./gradlew -Pagent testNative # Builds image using configuration acquired by agent.
```
Same can be achieved by setting corresponding DSL option.

### Testing:
This plugin supports running JUnit Platform tests using dedicated feature.
In order for this feature to register required tests and run them, you either need to:
* run:
    ```bash
    ./gradlew test
    ```
    before running `testNative` target like
    ```bash
    ./gradlew testNative
    ```
OR
* invoke `testNative` target using experimental flag
    ```bash
    ./gradlew -DtestDiscovery testNative
    ```
---

*You can also take a look at example project [here](../examples/gradle).*

## Building
Building of plugin itself should be as simple as:
```bash
./gradlew publishToMavenLocal
```

In order to run testing part of this plugin you need to get (or build) corresponding `junit-platform-feature`. More information about it is available [here](https://github.com/graalvm/native-image-configuration/blob/main/junit-platform-native/junit-platform-native-feature/README.md#Building).

*You can also take a look at CI workflow [here](../.github/workflows/native-image-gradle-plugin.yml).*


