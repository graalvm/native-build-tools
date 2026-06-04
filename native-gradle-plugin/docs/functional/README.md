# Functional Specification

The `native-gradle-plugin` module provides the Gradle plugin identified as
`org.graalvm.buildtools.native`. It turns Gradle projects, tasks, providers, and the
`graalvmNative` DSL into the shared Native Build Tools behavior defined by root and common specs.
The focused files below own the citable Gradle functional contracts for
§GOAL-gradle-plugin-native-image-workflows,
§GOAL-gradle-plugin-idiomatic-gradle-integration, and
§GOAL-gradle-plugin-behavior-stays-aligned-with-shared-contract, constrained by
§REQ-gradle-plugin-gradle-model-compatibility and §REQ-gradle-plugin-task-surface-stability.

## Functional Areas

| File | Holds |
| --- | --- |
| [plugin-model.md](plugin-model.md) | Plugin identity, `graalvmNative` extension, default binaries, custom binaries, and activation examples (§FS-gradle-plugin-model). |
| [native-image-tasks.md](native-image-tasks.md) | User-facing native compile, run, test, alias, command-line override, and task surface behavior (§FS-gradle-native-image-tasks). |
| [native-image-invocation.md](native-image-invocation.md) | Native Image executable discovery, version gates, command-line construction, argument files, classpath analysis, and parallel build behavior (§FS-gradle-native-image-invocation). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource autodetection, generated resource config, reachability metadata, missing-metadata reports, and dynamic access metadata (§FS-gradle-resources-and-metadata). |
| [tracing-agent.md](tracing-agent.md) | Tracing-agent enablement, instrumented tasks, modes, output layout, and metadata copy (§FS-gradle-tracing-agent). |
| [native-tests.md](native-tests.md) | Native test task integration, execution, compatibility mode, and command examples (§FS-gradle-native-tests). |

Use the focused IDs above for code and test citations.
