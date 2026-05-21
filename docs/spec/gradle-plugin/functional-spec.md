# FS-001-gradle-plugin-native-image-workflow: The Gradle plugin wires Native Image behavior into Gradle builds

The `native-gradle-plugin` module provides the Gradle plugin identified as
`org.graalvm.buildtools.native`. Applying that plugin gives a Gradle project a GraalVM extension,
native-image related tasks, command-line providers, metadata tasks, and test integration that are
expressed using Gradle's plugin, task, provider, and configuration-cache conventions.

## Required behavior

- Expose a Gradle DSL for configuring binaries, runtime options, compile options, resources,
  reachability metadata repositories, and agent modes.
- Register native build and run tasks that locate the `native-image` executable, assemble the
  expected command line, and execute it through Gradle task inputs and outputs.
- Integrate with Java application, Java library, test, custom source set, Kotlin, multi-project,
  resource, reflection, metadata repository, and layered application scenarios represented under
  `samples/`.
- Reuse common utilities and reachability metadata support rather than reimplementing shared
  behavior in Gradle-only code, as required by §GOAL-002-shared-native-image-behavior-stays-consistent.

## Verification surface

The module's unit tests cover task and plugin behavior. Its functional tests exercise sample
projects through Gradle TestKit and the repository's common test repository.
