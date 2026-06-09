# Functional Specification

The `native-gradle-plugin` module provides the Gradle plugin identified as
`org.graalvm.buildtools.native`. It turns Gradle projects, tasks, providers, and the
`graalvmNative` DSL into the shared Native Build Tools behavior defined by root and common specs.
The focused files below own the citable Gradle functional contracts for
[§GOAL-native-workflows](../goals.md#goal-native-workflows-gradle-users-can-use-native-image-through-gradle-native-workflows),
[§GOAL-idiomatic-gradle](../goals.md#goal-idiomatic-gradle-gradle-integration-follows-gradle-idioms-and-conventions), and
[§GOAL-shared-alignment](../goals.md#goal-shared-alignment-gradle-behavior-stays-aligned-with-shared-plugin-behavior), constrained by
[§REQ-gradle-model](../requirements.md#req-gradle-model-the-gradle-plugin-preserves-gradle-model-compatibility) and [§REQ-task-surface](../requirements.md#req-task-surface-gradle-task-and-dsl-names-remain-stable-across-compatible-releases).

## Functional Areas

| File | Holds |
| --- | --- |
| [plugin-model.md](plugin-model.md) | Plugin identity, `graalvmNative` extension, default binaries, custom binaries, and activation examples ([§FS-plugin-model](plugin-model.md#fs-plugin-model-gradle-plugin-activation-and-dsl-model)). |
| [native-image-tasks.md](native-image-tasks.md) | User-facing native compile, run, test, alias, command-line override, and task surface behavior ([§FS-native-tasks](native-image-tasks.md#fs-native-tasks-gradle-native-image-tasks-build-and-run-native-image-outputs)). |
| [native-image-invocation.md](native-image-invocation.md) | Native Image executable discovery, version gates, command-line construction, argument files, classpath analysis, and parallel build behavior ([§FS-native-invocation](native-image-invocation.md#fs-native-invocation-gradle-tasks-construct-and-execute-native-image-invocations)). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource autodetection, generated resource config, reachability metadata, missing-metadata reports, and dynamic access metadata ([§FS-resources-metadata](resources-and-metadata.md#fs-resources-metadata-gradle-tasks-generate-resources-and-consume-reachability-metadata)). |
| [tracing-agent.md](tracing-agent.md) | Tracing-agent enablement, instrumented tasks, modes, output layout, and metadata copy ([§FS-tracing-agent](tracing-agent.md#fs-tracing-agent-gradle-tasks-attach-and-post-process-native-image-tracing-agent-metadata)). |
| [native-tests.md](native-tests.md) | Native test task integration, execution, compatibility mode, and command examples ([§FS-native-tests](native-tests.md#fs-native-tests-gradle-tasks-compile-and-run-native-junit-tests)). |

Use the focused IDs above for code and test citations.
