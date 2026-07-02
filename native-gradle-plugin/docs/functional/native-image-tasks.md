# FS-native-tasks: Gradle native-image tasks build and run Native Image outputs

Native image tasks are the user-facing Gradle commands for building, running, and experimenting
with native executables. They adapt [§root/FS-native-builds](../../../docs/spec/functional/native-image-builds.md#fs-native-builds-both-plugins-build-native-images-from-build-tool-project-state) to Gradle task inputs and
outputs.

## 1. Compile tasks

`nativeCompile` builds the `main` binary. It consumes the binary classpath, main class or
shared-library setting, build arguments, configuration directories, generated resources,
reachability metadata, optional classpath JAR, argument-file setting, layer and PGO options,
environment variables, system properties, and JVM arguments.

`nativeTestCompile` builds the native test binary described by [§root/FS-native-tests](../../../docs/spec/functional/native-tests.md#fs-native-tests-both-plugins-compile-and-execute-junit-tests-as-a-native-image).
It uses compiled test classes, test resources, the test runtime classpath, JUnit native support,
selected test identifiers, and the `test` binary options.

Every custom binary must receive a derived `native<Binary>Compile` task. All compile tasks must
declare Gradle inputs and outputs for the selected options and generated files, including the
binary's classpath and reachability-metadata exclusions, so Gradle can skip, cache, or rerun them
consistently.

## 2. Run tasks

`nativeRun` executes the output of `nativeCompile` for the `main` binary and passes runtime
arguments from the binary configuration. When layered Native Image output is used, it sets up layer
library paths. Custom runnable binaries receive derived run tasks that execute their own
compile-task output.

`nativeTest` executes the output of `nativeTestCompile` unless native test execution is skipped.
A failing native test executable must fail the Gradle build.

## 3. Deprecated task aliases

The plugin must keep compatibility aliases for deprecated task names where they still exist. An
alias should depend on the replacement task and warn users to use the current name, protecting
[§REQ-task-surface](../requirements.md#req-task-surface-gradle-task-and-dsl-names-remain-stable-across-compatible-releases).

## 4. Command-line overrides

Compile tasks must expose task options for image name, main class, debug, verbose, fallback, quick
build, rich output, PGO instrumentation, build args, forced build args, fat JAR mode, system
properties, environment variables, JVM args, and forced JVM args. These one-off overrides feed the
same option objects as the DSL, keeping command-line experimentation aligned with
[§root/FS-option-precedence](../../../docs/spec/functional/option-precedence.md#fs-option-precedence-command-line-input-and-durable-configuration-produce-one-option-state).

```bash
./gradlew nativeCompile --quick-build-native --verbose --image-name demo-dev
./gradlew nativeCompile --build-args=--initialize-at-build-time=com.example
./gradlew nativeCompile --force-build-args=--no-fallback
```

## 5. Override precedence

Command-line task options and `-P` controls override DSL configuration for a single invocation
rather than merging with it. A setter such as `--image-name` calls `set(...)` on the same property
the DSL populates, so the command-line value replaces the DSL value for that build.

Build arguments are the documented exception: `--build-args` appends to configured arguments,
while `--force-build-args` replaces them. The `-Pagent` property overrides the configured agent
default mode as in [§FS-tracing-agent.1](tracing-agent.md#1-agent-enablement). Because every source writes to one option object,
behavior depends on the final value, not on whether the value came from DSL or the command line.

## 6. Task surface examples

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
