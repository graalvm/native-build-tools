# FS-tracing-agent: Gradle tasks attach and post-process Native Image tracing-agent metadata

The Gradle plugin exposes the shared tracing-agent workflow from
§root/FS-tracing-agent without requiring users to edit JVM task command lines by hand.

## 1. Agent enablement

The agent can be enabled by DSL configuration or with the `-Pagent` Gradle property. When the
property names a mode, that mode must override the configured default mode for the instrumented
run.

## 2. Instrumented tasks

Every task that implements `JavaForkOptions` is eligible for instrumentation. The
`tasksToInstrumentPredicate` setting may narrow that set. Non-matching tasks must be skipped
without failing the build.

## 3. Agent modes

The Gradle DSL must expose standard, conditional, direct, and disabled agent modes using the
shared agent mode behavior from §common/FS-common-libraries.3. Conditional mode must support
user-code and extra filters; direct mode must allow users to pass native agent options, including
`{output_dir}` substitution.

## 4. Agent output layout

Agent output must be written under `build/native/agent-output/<taskName>` unless users configure a
direct mode output location. Generated output must be suitable for later merge and copy steps.

## 5. Metadata copy

`metadataCopy` copies or merges agent output from configured input tasks into configured output
directories. Command-line options on `metadataCopy` may select task names and destination
directories for ad hoc use, exposing the shared agent post-processing workflow from
§root/FS-tracing-agent.

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
