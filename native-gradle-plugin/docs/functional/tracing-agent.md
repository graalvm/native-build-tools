# FS-tracing-agent: Gradle tasks attach and post-process Native Image tracing-agent metadata

The Gradle plugin exposes the shared tracing-agent workflow from
[§root/FS-tracing-agent](../../../docs/spec/functional/tracing-agent.md#fs-tracing-agent-both-plugins-attach-the-native-image-tracing-agent-and-post-process-its-output) without requiring users to edit JVM task command lines by hand.

## 1. Agent enablement

The agent can be enabled by DSL configuration or with the `-Pagent` Gradle property. When the
property names a mode, that mode must override the configured default mode for the instrumented
run. The default mode convention is `standard`, except projects that apply Gradle's
`java-library` plugin use `conditional` as the default convention.

## 2. Instrumented tasks

Every task that implements `JavaForkOptions` is eligible for instrumentation. The
`tasksToInstrumentPredicate` setting may narrow that set. Non-matching tasks must be skipped
without failing the build. In Groovy DSL, the predicate may be assigned directly as a closure;
its result must determine whether each eligible task is instrumented.

### 2.1 Agent Java runtime

When agent instrumentation is enabled, the task must prefer the Java executable from a valid
`GRAALVM_HOME`. If that is unavailable, the task may use `JAVA_HOME` only when the installation
identifies itself as GraalVM. A regular JDK in `JAVA_HOME` must not replace the task's configured
launcher or executable. When instrumentation is disabled, the plugin must leave the task's Java
runtime selection unchanged.

## 3. Agent modes

The Gradle DSL must expose standard, conditional, direct, and disabled agent modes using the
shared agent mode behavior from [§common/FS-common-libraries.3](../../../common/docs/functional-spec.md#3-native-image-tracing-agent). Conditional mode must support
user-code and extra filters; direct mode must allow users to pass native agent options, including
`{output_dir}` substitution. Disabled mode must leave eligible JVM tasks uninstrumented and must
not create agent output for those tasks.

## 4. Agent output layout

Agent output must be written under `build/native/agent-output/<taskName>` unless users configure a
direct mode output location. When a task is instrumented, the Gradle task output must report the
Gradle-managed agent output directory so users can find collected metadata without verbose logging,
aligning with [§root/GOAL-concise-actionable-output](../../../docs/spec/goals.md#goal-concise-actionable-output-build-output-is-concise-actionable-and-token-efficient). Generated output must be suitable for later
merge and copy steps.

## 5. Metadata copy

`metadataCopy` copies or merges agent output from configured input tasks into configured output
directories. Command-line options on `metadataCopy` may select task names and destination
directories for ad hoc use, exposing the shared agent post-processing workflow from
[§root/FS-tracing-agent](../../../docs/spec/functional/tracing-agent.md#fs-tracing-agent-both-plugins-attach-the-native-image-tracing-agent-and-post-process-its-output).

Agent post-processing with `native-image-utils` must discover the corresponding `native-image`
executable through the instrumented task launcher or the `metadataCopy` launcher when such a launcher is configured,
and otherwise preserve the executable fallbacks from [§FS-native-invocation.1](native-image-invocation.md#1-executable-discovery).

## 6. Agent example

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
