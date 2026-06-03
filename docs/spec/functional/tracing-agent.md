# FS-tracing-agent-workflows: Both plugins attach the Native Image tracing agent and post-process its output

Both plugins must let users run a normal JVM task or test with the Native Image tracing agent
(§GLOSS-tracing-agent), collect the resulting metadata, and merge or copy it into a stable
metadata directory. Users must not have to assemble `-agentlib:native-image-agent=...` strings by
hand for common cases. Shared agent mode and post-processing behavior is
§common/FS-common-libraries.3 and §common/FS-common-libraries.4. Gradle adapts through
§gradle/FS-gradle-plugin.5; Maven adapts through §maven/FS-maven-plugin.5.

## 1. Enablement

The agent is disabled by default. Both plugins must offer two enablement paths:

- A durable configuration switch (`graalvmNative.agent` in Gradle, `<agent>` in Maven).
- A short-lived command-line override (`-Pagent[=mode]` in Gradle, `-Dagent=true|false` in Maven).

The command-line override must compose with durable configuration as defined by
§FS-option-precedence: a property-supplied mode overrides the configured default mode for a single
invocation, and the property must be able to disable an agent that is enabled in the build file.

## 2. Agent modes

Both plugins must expose four modes from the shared model in §common/FS-common-libraries.3.1:

| Mode | Behavior |
| --- | --- |
| `standard` | Collect all dynamic access. |
| `conditional` | Conditional metadata with user-code filter and optional extra filter. |
| `direct` | Pass user-supplied agent options through to `native-image-agent`, with `{output_dir}` substitution. |
| `disabled` | No instrumentation; equivalent to omitting the agent. |

Advanced agent options (caller filters, access filters, predefined classes, unsafe allocation
tracing, reflection metadata tracking) must be expressible in both build tools using the shared
model so the same configuration produces the same `native-image-agent` command line.

## 3. Attaching the agent

Each plugin attaches the agent at the right execution hook for its build tool:

- **Gradle:** every task that implements `JavaForkOptions` is eligible; the
  `tasksToInstrumentPredicate` setting narrows the set. Non-matching tasks must be skipped
  silently rather than failing the build.
- **Maven:** the agent attaches to `surefire` test runs and to `exec`-style application runs
  according to plugin configuration.

The output directory layout must be predictable so post-processing can find it:

| Build tool | Stage | Default output directory |
| --- | --- | --- |
| Gradle | any instrumented task | `build/native/agent-output/<taskName>` |
| Maven | tests | `target/native/agent-output/test` |
| Maven | application | `target/native/agent-output/main` |

`direct` mode may override the default location through the user-supplied agent options.

## 4. Merge and copy

Post-processing must let users either merge agent output with the existing destination metadata
or replace it. Merging must invoke `native-image-configure` from the same Native Image
installation used for the build when possible.

| Build tool | Merge | Copy |
| --- | --- | --- |
| Gradle | configured through `agent.metadataCopy { mergeWithExisting = true }` | `metadataCopy` |
| Maven | `<metadataCopy><merge>true</merge></metadataCopy>` | `native:metadata-copy` |
| Maven (direct merge utility) | — | `native:merge-agent-files` |
