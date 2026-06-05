# REQ-gradle-plugin-gradle-model-compatibility: The Gradle plugin preserves Gradle model compatibility

The Gradle plugin must integrate with Gradle Java, Application, Java Library, testing, provider,
toolchain, and configuration-cache models without replacing them. It may add Native Image-specific
tasks and extension state, but those additions must remain compatible with Gradle's task graph and
lazy configuration rules.

Gradle-facing implementation must use Gradle APIs and conventions for Gradle behavior: lazy
providers for deferred values, declared task inputs and outputs, build services for shared state,
toolchains for Java and Native Image lookup, attributes and artifact transforms for dependency
graph integration, and configuration-cache-compatible task wiring. Behavior that is genuinely
build-tool-neutral should move to `common` instead of reimplementing Gradle-specific logic.
This requirement constrains §FS-gradle-plugin-model and §AR-gradle-plugin.2, and supports
§GOAL-gradle-plugin-idiomatic-gradle-integration.

# REQ-gradle-plugin-task-surface-stability: Gradle task and DSL names remain stable across compatible releases

Gradle task names, extension names, binary names, and command-line options are user-facing
compatibility surfaces. Changes to those names must follow the repository compatibility
requirement in §root/REQ-backwards-compatibility-across-plugin-versions and the alias behavior in
§FS-gradle-native-image-tasks.3.
