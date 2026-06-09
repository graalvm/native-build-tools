# REQ-gradle-model: The Gradle plugin preserves Gradle model compatibility

The Gradle plugin must integrate with Gradle Java, Application, Java Library, testing, provider,
toolchain, and configuration-cache models without replacing them. It may add Native Image-specific
tasks and extension state, but those additions must remain compatible with Gradle's task graph and
lazy configuration rules.

Gradle-facing implementation must use Gradle APIs and conventions for Gradle behavior: lazy
providers for deferred values, declared task inputs and outputs, build services for shared state,
toolchains for Java and Native Image lookup, attributes and artifact transforms for dependency
graph integration, and configuration-cache-compatible task wiring. Behavior that is genuinely
build-tool-neutral should move to `common` instead of reimplementing Gradle-specific logic.
This requirement constrains [§FS-plugin-model](functional/plugin-model.md#fs-plugin-model-gradle-plugin-activation-and-dsl-model) and [§AR-gradle-plugin.2](architecture.md#2-extension-and-option-model), and supports
[§GOAL-idiomatic-gradle](goals.md#goal-idiomatic-gradle-gradle-integration-follows-gradle-idioms-and-conventions).

# REQ-task-surface: Gradle task and DSL names remain stable across compatible releases

Gradle task names, extension names, binary names, and command-line options are user-facing
compatibility surfaces. Changes to those names must follow the repository compatibility
requirement in [§root/REQ-backwards-compatibility](../../docs/spec/requirements.md#req-backwards-compatibility-plugin-upgrades-keep-existing-gradle-and-maven-builds-working) and the alias behavior in
[§FS-native-tasks.3](functional/native-image-tasks.md#3-deprecated-task-aliases).
