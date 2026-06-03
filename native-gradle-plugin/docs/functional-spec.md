# FS-gradle-plugin: The Gradle plugin wires Native Image behavior into Gradle builds

The `native-gradle-plugin` module provides the Gradle plugin identified as
`org.graalvm.buildtools.native`. It turns Gradle projects, tasks, providers, and the
`graalvmNative` DSL into the shared Native Build Tools behavior defined by
§root/FS-plugin-common-behavior, §root/FS-native-tests, and
§common/FS-common-libraries. This functional contract realizes
§GOAL-gradle-plugin-native-image-workflows under §GRUND-gradle-plugin-purpose, keeps Gradle
behavior aligned through §GOAL-gradle-plugin-behavior-stays-aligned-with-shared-contract, and is
constrained by §REQ-gradle-plugin-gradle-model-compatibility and
§REQ-gradle-plugin-task-surface-stability.

## At a Glance

| User wants to... | They configure | They run | Main output |
| --- | --- | --- | --- |
| Build the application image | `application.mainClass` and `graalvmNative.binaries.main` | `./gradlew nativeCompile` | `build/native/nativeCompile/<imageName>` |
| Run the application image | runtime args on the `main` binary or `nativeRun` task | `./gradlew nativeRun` | native process result |
| Build native tests | normal Gradle `test` source set plus optional `graalvmNative.binaries.test` | `./gradlew nativeTestCompile` | `build/native/nativeTestCompile/<imageName>` |
| Run native tests | JUnit test setup and native test options | `./gradlew nativeTest` | Gradle test task success/failure |
| Generate resource config | binary resource settings | `./gradlew generateResourcesConfigFile` | generated `resource-config.json` under `build/native/` |
| Use reachability metadata | `graalvmNative.metadataRepository` | `./gradlew nativeCompile` | selected metadata passed to Native Image |
| Collect agent metadata | `graalvmNative.agent` or `-Pagent` | JVM task/test, then `./gradlew metadataCopy` | copied or merged metadata files |

The sections below give code and tests narrow citation targets such as §FS-gradle-plugin.2.1 for
compile tasks, §FS-gradle-plugin.4.4 for missing-metadata reports, or §FS-gradle-plugin.5.5 for
metadata copy.

## 1. Plugin activation and Gradle model

The plugin should behave like a normal Gradle Java plugin extension: users apply one plugin ID and
configure native behavior beside their existing `application`, `java-library`, source set, and test
configuration.

### 1.1 Plugin identity

The plugin ID is `org.graalvm.buildtools.native`. Applying it alone must not force a Java model into
the project. Java-dependent native tasks are registered when a Java plugin is present, so projects
can apply plugins in either order without relying on eager configuration.

### 1.2 Extension surface

The plugin must expose a `graalvmNative` extension. This is the durable Gradle DSL for native
binaries, Native Image options, generated resources, reachability metadata, native tests, and
tracing-agent workflows from §root/FS-plugin-common-behavior.

For a typical application, users configure the same binary that `nativeCompile` builds:

```groovy
graalvmNative {
    binaries {
        main {
            imageName = 'demo'
            buildArgs.add('--verbose')
            quickBuild = true
        }
    }
}
```

The extension feeds the option objects consumed by compile, run, resource-generation, metadata,
and native-test tasks. Users should not repeat `imageName`, `mainClass`, `buildArgs`, metadata
directories, or resource settings separately on each task.

### 1.3 Default binaries

For Java application projects, the plugin must create a `main` binary whose `mainClass` convention
comes from Gradle's `application` extension when available. `nativeCompile` builds that image and
`nativeRun` executes it. For Java library projects, the main binary defaults to shared-library
output.

```groovy
plugins {
    id 'application'
    id 'org.graalvm.buildtools.native'
}

application {
    mainClass.set('com.example.Main')
}
```

The plugin must also create a `test` binary connected to the default `test` task and `test` source
set so `nativeTest` can build and run native JUnit tests without a separate binary declaration.

### 1.4 Custom binaries

Users may add entries to the `binaries` container for extra source sets or entry points. Each entry
must create matching compile and run tasks with predictable task names derived from the binary
name. Custom binaries apply §root/FS-native-image-builds without forcing users outside the
plugin's option model.

### 1.5 Activation examples

The minimal application flow is `./gradlew nativeCompile` followed by `./gradlew nativeRun`.
Test builds use `./gradlew nativeTestCompile` to inspect the image or `./gradlew nativeTest` to
compile and execute it through the native test launcher.

```bash
./gradlew nativeCompile
./gradlew nativeRun
./gradlew nativeTestCompile
./gradlew nativeTest
```

## 2. Native image task behavior

Native image tasks are the user-facing Gradle commands for building, running, and experimenting
with native executables. They adapt §root/FS-native-image-builds to Gradle task inputs and
outputs.

### 2.1 Compile tasks

`nativeCompile` builds the `main` binary. It consumes the binary classpath, main class or
shared-library setting, build arguments, configuration directories, generated resources,
reachability metadata, optional classpath JAR, argument-file setting, layer and PGO options,
environment variables, system properties, and JVM arguments.

`nativeTestCompile` builds the native test binary described by §root/FS-native-tests.
It uses compiled test classes, test resources, the test runtime classpath, JUnit native support,
selected test identifiers, and the `test` binary options.

Every custom binary must receive a derived `native<Binary>Compile` task. All compile tasks must
declare Gradle inputs and outputs for the selected options and generated files so Gradle can skip,
cache, or rerun them consistently.

### 2.2 Run tasks

`nativeRun` executes the output of `nativeCompile` for the `main` binary and passes runtime
arguments from the binary configuration. When layered Native Image output is used, it sets up layer
library paths. Custom runnable binaries receive derived run tasks that execute their own
compile-task output.

`nativeTest` executes the output of `nativeTestCompile` unless native test execution is skipped.
A failing native test executable must fail the Gradle build.

### 2.3 Deprecated task aliases

The plugin must keep compatibility aliases for deprecated task names where they still exist. An
alias should depend on the replacement task and warn users to use the current name, protecting
§REQ-gradle-plugin-task-surface-stability.

### 2.4 Command-line overrides

Compile tasks must expose task options for image name, main class, debug, verbose, fallback, quick
build, rich output, PGO instrumentation, build args, forced build args, fat JAR mode, system
properties, environment variables, JVM args, and forced JVM args. These one-off overrides feed the
same option objects as the DSL, keeping command-line experimentation aligned with
§root/FS-option-precedence.

```bash
./gradlew nativeCompile --quick-build-native --verbose --image-name demo-dev
./gradlew nativeCompile --build-args=--initialize-at-build-time=com.example
./gradlew nativeCompile --force-build-args=--no-fallback
```

### 2.5 Override precedence

Command-line task options and `-P` controls override DSL configuration for a single invocation
rather than merging with it. A setter such as `--image-name` calls `set(...)` on the same property
the DSL populates, so the command-line value replaces the DSL value for that build.

Build arguments are the documented exception: `--build-args` appends to configured arguments,
while `--force-build-args` replaces them. The `-Pagent` property overrides the configured agent
default mode as in §FS-gradle-plugin.5.1. Because every source writes to one option object,
behavior depends on the final value, not on whether the value came from DSL or the command line.

### 2.6 Task surface examples

The primary task surface is `nativeCompile`, `nativeRun`, `nativeTestCompile`, `nativeTest`,
`generateResourcesConfigFile`, `collectReachabilityMetadata`, `listLibrariesMissingMetadata`, and
`metadataCopy`. These tasks should be discoverable through normal Gradle task listing and keep
generated Native Image state under `build/native/`.

```bash
./gradlew nativeCompile
./gradlew nativeRun
./gradlew nativeTestCompile
./gradlew nativeTest
./gradlew generateResourcesConfigFile
./gradlew collectReachabilityMetadata
./gradlew listLibrariesMissingMetadata
./gradlew metadataCopy
```

## 3. Native Image invocation

Native Image invocation covers executable lookup, preflight checks, command-line assembly, and
process execution.

### 3.1 Executable discovery

Compile and metadata tasks must find `native-image` from the configured Gradle Java launcher or
toolchain when toolchain detection is enabled. When detection is disabled or no launcher supplies
Native Image, the plugin must use GraalVM/JDK environment and path fallbacks. Failure messages must
tell the user which lookup path was attempted.

### 3.2 Version and schema gates

When users configure a required Native Image version, compile tasks must check the discovered
version before building. When reachability metadata is enabled, tasks must validate repository
metadata against the schema expected by the discovered Native Image major version before passing
that metadata to `native-image`.

### 3.3 Command-line construction

The command line must combine classpath, module path where applicable, output name, main class,
boolean image flags, build arguments, JVM arguments, system properties, environment variables,
configuration directories, generated resources, reachability metadata, layer options
(§root/GLOSS-layered-image), and PGO options (§root/GLOSS-pgo). Shared escaping and argument-file
conversion must come from common utilities rather than Gradle-only string handling, keeping Gradle
aligned with §root/FS-option-precedence.

### 3.4 Argument files

The plugin must support Native Image argument files for command lines that should not be passed as
plain process arguments. Argument-file generation must preserve argument semantics and use paths
relative to the selected working directory where Native Image requires that form.

### 3.5 Classpath JAR and artifact analysis

When configured to use a classpath JAR, the compile task must pass the generated JAR instead of an
exploded classpath. The plugin may analyze runtime classpath JARs through Gradle artifact
transforms to discover packages and resource behavior, but that transform output is an internal
detail. The fat-jar form is defined in §root/GLOSS-fat-jar.

### 3.6 Parallel native builds

The plugin must limit concurrent Native Image builds through a Gradle build service. Users can set
the limit with `org.graalvm.buildtools.max.parallel.builds` or
`GRAALVM_BUILDTOOLS_MAX_PARALLEL_BUILDS`; otherwise the plugin chooses a conservative default from
available processors.

## 4. Resources and reachability metadata

The plugin exposes the shared metadata and resource contracts in
§root/FS-resources-and-metadata and §common/FS-common-libraries through Gradle DSL and tasks.

### 4.1 Resource autodetection

When a binary enables resource autodetection, the plugin must scan that binary's runtime classpath
and generate `resource-config.json`. If a classpath entry already contains Native Image resource
configuration and existing configuration should not be ignored, the plugin must not duplicate
resources from that entry.

The main binary resource task is `generateResourcesConfigFile`. Custom binaries receive
`generate<Binary>ResourcesConfigFile` tasks. The test binary's generated resource task contributes
to `nativeTestCompile`.

### 4.2 Generated resource configuration

Generated resource configuration must be placed under the configured generated-resources directory
and added to the binary's configuration file directories so the compile task consumes it
automatically.

### 4.3 Reachability metadata collection

`collectReachabilityMetadata` resolves metadata for the runtime classpath from the configured
metadata repository URI, version, exclusions, and module-to-config-version overrides. Its output
directory must be consumable by native compile tasks and must contain only metadata selected for
the binary's dependency graph.

### 4.4 Missing metadata reports

`listLibrariesMissingMetadata` inspects direct runtime dependencies, compares them with the
configured reachability metadata repository, writes a JSON report, and may create GitHub issues
when issue-creation settings are supplied. The task reports missing metadata without modifying the
native compile task inputs.

### 4.5 Dynamic access metadata

When a binary is configured to emit a Native Image build report, the plugin must generate
dynamic-access metadata before invoking Native Image. The task uses the configured reachability
metadata repository and runtime classpath graph, then makes the result available as generated
Native Image configuration. The metadata is defined in §root/GLOSS-dynamic-access-metadata.

The main binary task is `generateDynamicAccessMetadata`; custom binaries receive
`generate<Binary>DynamicAccessMetadata`. The task output is added to the binary's classpath only
when the binary requests a Native Image build report.

### 4.6 Metadata examples

Repository metadata is configured through `graalvmNative.metadataRepository`; exclusions are
configured on binaries through `excludeConfig`. `nativeCompile` consumes selected repository
metadata, while `listLibrariesMissingMetadata` reports uncovered dependencies separately.

```groovy
graalvmNative {
    metadataRepository {
        enabled = true
    }
    binaries.all {
        excludeConfig.put("com.example:library:1.0", [".*"])
    }
}
```

```bash
./gradlew nativeCompile
./gradlew listLibrariesMissingMetadata
```

## 5. Native Image tracing agent

The Gradle plugin exposes the shared tracing-agent workflow from
§root/FS-tracing-agent-workflows without requiring users to edit JVM task command lines by hand.

### 5.1 Agent enablement

The agent can be enabled by DSL configuration or with the `-Pagent` Gradle property. When the
property names a mode, that mode must override the configured default mode for the instrumented
run.

### 5.2 Instrumented tasks

Every task that implements `JavaForkOptions` is eligible for instrumentation. The
`tasksToInstrumentPredicate` setting may narrow that set. Non-matching tasks must be skipped
without failing the build.

### 5.3 Agent modes

The Gradle DSL must expose standard, conditional, direct, and disabled agent modes using the
shared agent mode behavior from §common/FS-common-libraries.3. Conditional mode must support
user-code and extra filters; direct mode must allow users to pass native agent options, including
`{output_dir}` substitution.

### 5.4 Agent output layout

Agent output must be written under `build/native/agent-output/<taskName>` unless users configure a
direct mode output location. Generated output must be suitable for later merge and copy steps.

### 5.5 Metadata copy

`metadataCopy` copies or merges agent output from configured input tasks into configured output
directories. Command-line options on `metadataCopy` may select task names and destination
directories for ad hoc use, exposing the shared agent post-processing workflow from
§root/FS-tracing-agent-workflows.

### 5.6 Agent example

Agent collection is invoked by running an eligible JVM task with `-Pagent`; post-processing is
invoked with `metadataCopy`. The default output location for a task such as `test` is
`build/native/agent-output/test` unless direct mode changes it.

```groovy
graalvmNative {
    agent {
        defaultMode = "standard"
        metadataCopy {
            mergeWithExisting = true
            inputTaskNames.add("test")
            outputDirectories.add("build/native/metadataCopyTest")
        }
    }
}
```

```bash
./gradlew nativeTest -Pagent
./gradlew metadataCopy
```

## 6. Native tests

Gradle native tests let users keep their normal JUnit test source set and ask the plugin to build
and execute those tests as a native image. Runtime semantics are defined by
§root/FS-native-tests.

### 6.1 Test task integration

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

### 6.2 Native test execution

`nativeTestCompile` builds the native test image. `nativeTest` runs that image unless the test
support DSL disables native testing or the requested task graph only builds the test image. Native
test failures must fail the Gradle build in the same way Java test failures do.

### 6.3 Compatibility mode

When Native Image compatibility mode is detected, Gradle native test behavior may use the original
JUnit ConsoleLauncher path rather than the Native Build Tools launcher path, as described by
§root/FS-native-tests.5.

### 6.4 Native test command examples

`nativeTestCompile` is the image-generation entry point; `nativeTest` is the CI entry point because
it compiles the test image, executes it, and reports failures through Gradle.

```bash
./gradlew nativeTestCompile
./gradlew nativeTest
```

## 7. Verification surface

Unit tests cover task and plugin behavior that can be exercised without running full sample
builds. Functional tests exercise Gradle sample projects through TestKit, with scenario ownership
defined by §gradle/E2E-gradle-plugin-functional-tests and fixture ownership defined by
§root/AR-build-infrastructure.4.1.
