# Functional Specification

The `native-gradle-plugin` module provides the Gradle plugin identified as
`org.graalvm.buildtools.native`. It turns Gradle projects, tasks, providers, and the
`graalvmNative` DSL into the shared Native Build Tools behavior defined by root and common specs.
The focused files below own the citable Gradle functional contracts for
§GOAL-native-workflows,
§GOAL-idiomatic-gradle, and
§GOAL-shared-alignment, constrained by
§REQ-gradle-model and §REQ-task-surface.

## Functional Areas

| File | Holds |
| --- | --- |
| [plugin-model.md](plugin-model.md) | Plugin identity, `graalvmNative` extension, default binaries, custom binaries, and activation examples (§FS-plugin-model). |
| [native-image-tasks.md](native-image-tasks.md) | User-facing native compile, run, test, alias, command-line override, and task surface behavior (§FS-native-tasks). |
| [native-image-invocation.md](native-image-invocation.md) | Native Image executable discovery, version gates, command-line construction, argument files, classpath analysis, and parallel build behavior (§FS-native-invocation). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource autodetection, generated resource config, reachability metadata, missing-metadata reports, and dynamic access metadata (§FS-resources-metadata). |
| [tracing-agent.md](tracing-agent.md) | Tracing-agent enablement, instrumented tasks, modes, output layout, and metadata copy (§FS-tracing-agent). |
| [native-tests.md](native-tests.md) | Native test task integration, execution, compatibility mode, and command examples (§FS-native-tests). |

Use the focused IDs above for code and test citations.
