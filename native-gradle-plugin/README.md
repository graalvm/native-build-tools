# Native Image Gradle Plugin
Gradle plugin for GraalVM Native Image building
![](https://github.com/graalvm/native-image-build-tools/actions/workflows/native-gradle-plugin.yml/badge.svg)

## Usage
> :information_source: Working GraalVM installation (with `native-image` installable and `GRAALVM_HOME` and/or `JAVA_HOME` environment variables set) is prequisite for successful *native-image* building.
>
> More information is available [here](../common/docs/GRAALVM_SETUP.md).

Add following to `plugins` section of your project's `build.gradle` / `build.gradle.kts`:

<details open>
<summary>
Groovy
</summary>

```groovy
plugins {
    // ...

    // Apply GraalVM Native Image plugin
    id 'org.graalvm.buildtools.native' version "${current_plugin_version}"
}
```
</details>

<details>
<summary>
Kotlin
</summary>

```kotlin
plugins {
    // ...

    // Apply GraalVM Native Image plugin
    id('org.graalvm.buildtools.native') version "${current_plugin_version}"
}
```

</details>


<br />

Additionally add the following to your `settings.gradle` / `settings.gradle.kts`:
```groovy
pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}
```
*(this step will be redundant once this plugin is published to the Gradle Plugin Portal).*

<br />

### DSL definition
This plugin is primarily configured using Gradle DSL. This can be achieved by adding `nativeBuild` and `nativeTest` sections to your `build.gradle` / `build.gradle.kts` like:

<details open>
<summary>
Groovy
</summary>

```groovy
nativeBuild {
  imageName = "application"
  mainClass = "org.test.Main" // Main class
  buildArgs("--no-server") // Arguments to be passed to native-image invocation
  debug = false // Determines if debug info should be generated
  verbose = false
  fallback = false
  classpath("dir1", "dir2") // Adds "dir1" and "dir2" to the classpath
  jvmArgs("flag") // Passes 'flag' directly to the JVM running the native image builder
  runtimeArgs("--help") // Passes '--help' to built image, during "nativeRun" task
  systemProperties = [name1: 'value1', name2: 'value2'] // Sets system properties for the native image builder
  agent = false // Can be also set on command line using '-Pagent'
}

nativeTest {
  agent = false // Can be also set on command line using '-Pagent'
  //...
  // all of the options from 'nativeBuild' block are supported here except for changing main class name.
  // Note that 'nativeBuild' configuration is separate to 'nativeTest' one and that they don't inherit settings from each other.
}
```

</details>

<details>
<summary>
Kotlin
</summary>

```kotlin
nativeBuild {
  imageName.set("application")
  mainClass.set("org.test.Main") // Main class
  buildArgs("--no-server") // Arguments to be passed to native-image invocation
  debug.set(false) // Determines if debug info should be generated
  verbose.set(false)
  fallback.set(false)
  classpath("dir1", "dir2") // Adds "dir1" and "dir2" to the classpath
  jvmArgs("flag") // Passes 'flag' directly to the JVM running the native image builder
  runtimeArgs("--help") // Passes '--help' to built image, during "nativeRun" task
  systemProperties.put("key1", "value1") // Sets a system property for the native-image builder
  agent.set(false) // Can be also set on command line using '-Pagent'
}

nativeTest {
  agent.set(false) // Can be also set on command line using '-Pagent'
  //...
  // all of the options from 'nativeBuild' block are supported here except for changing main class name
  // Note that 'nativeBuild' configuration is separate to 'nativeTest' one and that they don't inherit settings from each other
}
```

</details>

<br />


> :information_source: For options that can be set using command-line, if both DSL and command-line options are present, command-line options take precedence.

### Available tasks:
```
Application tasks
-----------------
nativeRun - Runs this project as a native-image application

Build tasks
-----------
nativeBuild - Builds native-image from this project.

Verification tasks
------------------
nativeTest - Runs native-image compiled tests.
nativeTestBuild - Builds native image with tests.
```

### Reflection support
If your project requires reflection, then [`native-image-agent`](https://docs.oracle.com/en/graalvm/enterprise/19/guide/reference/native-image/tracing-agent.html) run might be necessary.
This should be as easy as appending `-Pagent` to `run` and `nativeBuild`, or `test` and `nativeTest` task invocations:
```bash
./gradlew -Pagent run # Runs on JVM with native-image-agent.
./gradlew -Pagent nativeBuild # Builds image using configuration acquired by agent.

# For testing
./gradlew -Pagent test # Runs on JVM with native-image-agent.
./gradlew -Pagent nativeTest # Builds image using configuration acquired by agent.
```
Same can be achieved by setting corresponding DSL option.

### Testing:
This plugin supports running JUnit Platform tests using dedicated feature.
In order for this feature to register required tests and run them, you need to run:
```bash
./gradlew nativeTest
```

This will run tests in JVM mode then in native mode.

*You can also take a look at example project [here](../samples).*

## Building
Building of plugin itself should be as simple as:
```bash
./gradlew publishToMavenLocal
```

In order to run testing part of this plugin you need to get (or build) corresponding `junit-platform-native` artifact.

*You can also take a look at CI workflow [here](../.github/workflows/native-gradle-plugin.yml).*
