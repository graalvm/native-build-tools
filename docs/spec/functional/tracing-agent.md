# FS-tracing-agent: Both plugins attach the Native Image tracing agent and post-process its output

Both plugins must let users run a normal JVM task or test with the Native Image tracing agent
([§GLOSS-tracing-agent](../glossary.md#gloss-tracing-agent-native-image-tracing-agent)), collect the resulting metadata, and merge or copy it into a stable
metadata directory. Users must not have to assemble `-agentlib:native-image-agent=...` strings by
hand for common cases. Shared agent mode and post-processing behavior is
[§common/FS-common-libraries.3](../../../common/docs/functional-spec.md#3-native-image-tracing-agent) and [§common/FS-common-libraries.4](../../../common/docs/functional-spec.md#4-agent-metadata-post-processing). Gradle adapts through
[§gradle/FS-tracing-agent](../../../native-gradle-plugin/docs/functional/tracing-agent.md#fs-tracing-agent-gradle-tasks-attach-and-post-process-native-image-tracing-agent-metadata); Maven adapts through [§maven/FS-tracing-agent](../../../native-maven-plugin/docs/functional/tracing-agent.md#fs-tracing-agent-maven-goals-attach-and-post-process-native-image-tracing-agent-metadata).

## 1. Enablement

The agent is disabled by default. Both plugins must offer two enablement paths:

- A durable build-file configuration path.
- A short-lived command-line override.

The command-line override must compose with durable configuration as defined by
[§FS-option-precedence](option-precedence.md#fs-option-precedence-command-line-input-and-durable-configuration-produce-one-option-state): a property-supplied mode overrides the configured default mode for a single
invocation, and the property must be able to disable an agent that is enabled in the build file.
Plugin-specific configuration names are specified by [§gradle/FS-tracing-agent.1](../../../native-gradle-plugin/docs/functional/tracing-agent.md#1-agent-enablement) and
[§maven/FS-tracing-agent.1](../../../native-maven-plugin/docs/functional/tracing-agent.md#1-agent-enablement).

## 2. Agent modes

Both plugins must expose the modes from the shared model in [§common/FS-common-libraries.3.1](../../../common/docs/functional-spec.md#31-agent-modes).

Advanced agent options (caller filters, access filters, predefined classes, unsafe allocation
tracing, reflection metadata tracking) must be expressible in both build tools using the shared
model so the same configuration produces the same `native-image-agent` command line.

## 3. Attaching the agent

Each plugin attaches the agent at the right execution hook for its build tool and writes output to
a predictable location that post-processing can find.

`direct` mode may override the default location through the user-supplied agent options.
Plugin-specific execution hooks and output layouts are specified by
[§gradle/FS-tracing-agent.2](../../../native-gradle-plugin/docs/functional/tracing-agent.md#2-instrumented-tasks), [§gradle/FS-tracing-agent.4](../../../native-gradle-plugin/docs/functional/tracing-agent.md#4-agent-output-layout), and
[§maven/FS-tracing-agent.3](../../../native-maven-plugin/docs/functional/tracing-agent.md#3-agent-output).

## 4. Merge and copy

Post-processing must let users either merge agent output with the existing destination metadata
or replace it. Merging must invoke `native-image-configure` from the same Native Image
installation used for the build when possible.

Plugin-specific merge and copy entry points are specified by [§gradle/FS-tracing-agent.5](../../../native-gradle-plugin/docs/functional/tracing-agent.md#5-metadata-copy)
and [§maven/FS-tracing-agent.4](../../../native-maven-plugin/docs/functional/tracing-agent.md#4-merge-and-copy).
