# REQ-gradle-plugin-gradle-model-compatibility: The Gradle plugin preserves Gradle model compatibility

The Gradle plugin must integrate with Gradle Java, Application, Java Library, testing, provider,
toolchain, and configuration-cache models without replacing them. It may add Native Image-specific
tasks and extension state, but those additions must remain compatible with Gradle's task graph and
lazy configuration rules. This requirement constrains §FS-gradle-plugin.1 and
§AR-gradle-plugin.2.

# REQ-gradle-plugin-task-surface-stability: Gradle task and DSL names remain stable across compatible releases

Gradle task names, extension names, binary names, and command-line options are user-facing
compatibility surfaces. Changes to those names must follow the repository compatibility
requirement in §root/REQ-backwards-compatibility-across-plugin-versions and the alias behavior in
§FS-gradle-plugin.2.3.
