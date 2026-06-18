# FS-option-precedence: Command-line input and durable configuration produce one option state

Both plugins must keep command-line overrides and configured options predictable. The exact
precedence rules differ because Gradle task options and Maven parameter binding use different
models, but each plugin must document how temporary command-line input relates to durable build
configuration. Gradle precedence is [§gradle/FS-native-tasks.5](../../../native-gradle-plugin/docs/functional/native-image-tasks.md#5-override-precedence); Maven precedence is
[§maven/FS-config-model.5](../../../native-maven-plugin/docs/functional/configuration-model.md#5-override-precedence). This contract supports
[§GOAL-plugin-parity](../goals.md#goal-plugin-parity-shared-native-image-behavior-remains-consistent-across-gradle-and-maven).

## 1. Single option state

Every configuration source for a given build (durable build-file configuration, command-line task
or goal options, build-tool properties, environment variables where applicable) must write into
the same option object that the command-line constructor in [§FS-native-builds.2](native-image-builds.md#2-command-line-construction) reads.
Behavior must depend on the final value of that object, not on which source produced it.

## 2. Append versus replace

The append-vs-replace distinction must be explicit in both tools:

- `buildArgs` appends to durable configuration so users can add a single argument from the
  command line without losing the build's configured arguments.
- A documented replacement form replaces durable configuration so users can override the full
  argument list when investigating issues.

## 3. Cross-tool parity

Cross-plugin parity means equivalent user intent should produce equivalent Native Image behavior,
not that Gradle and Maven must expose identical flag names. When a user enables verbose output,
disables fallback, or sets a quick build flag in either tool, the resulting `native-image`
invocation must carry the same effective option.

## 4. Documented exceptions

Each plugin must explicitly document any parameter that does not follow its default precedence,
including tracing-agent toggles where a command-line property is intentionally modeled to win over
the build file for a single invocation. Undocumented exceptions are a parity bug. Plugin-specific
exceptions are specified by [§gradle/FS-native-tasks.5](../../../native-gradle-plugin/docs/functional/native-image-tasks.md#5-override-precedence),
[§gradle/FS-tracing-agent.1](../../../native-gradle-plugin/docs/functional/tracing-agent.md#1-agent-enablement), [§maven/FS-config-model.5](../../../native-maven-plugin/docs/functional/configuration-model.md#5-override-precedence), and
[§maven/FS-tracing-agent.1](../../../native-maven-plugin/docs/functional/tracing-agent.md#1-agent-enablement).
