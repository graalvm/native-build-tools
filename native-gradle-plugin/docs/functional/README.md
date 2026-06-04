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

## Functional Areas

| File | Holds |
| --- | --- |
| [plugin-model.md](plugin-model.md) | Plugin identity, `graalvmNative` extension, default binaries, custom binaries, and activation examples (§FS-gradle-plugin-model). |
| [native-image-tasks.md](native-image-tasks.md) | User-facing native compile, run, test, alias, command-line override, and task surface behavior (§FS-gradle-native-image-tasks). |
| [native-image-invocation.md](native-image-invocation.md) | Native Image executable discovery, version gates, command-line construction, argument files, classpath analysis, and parallel build behavior (§FS-gradle-native-image-invocation). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource autodetection, generated resource config, reachability metadata, missing-metadata reports, and dynamic access metadata (§FS-gradle-resources-and-metadata). |
| [tracing-agent.md](tracing-agent.md) | Tracing-agent enablement, instrumented tasks, modes, output layout, and metadata copy (§FS-gradle-tracing-agent). |
| [native-tests.md](native-tests.md) | Native test task integration, execution, compatibility mode, and command examples (§FS-gradle-native-tests). |

Use the focused IDs above for code and test citations. Use §FS-gradle-plugin only when citing the Gradle plugin functional contract as a whole.

## Verification Surface

Unit tests cover task and plugin behavior that can be exercised without running full sample
builds. Functional tests exercise Gradle sample projects through TestKit, with scenario ownership
defined by §gradle/E2E-gradle-plugin-functional-tests and fixture ownership defined by
§root/AR-build-infrastructure.4.1.
