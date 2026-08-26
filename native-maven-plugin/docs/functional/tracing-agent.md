# FS-tracing-agent: Maven goals attach and post-process Native Image tracing-agent metadata

The Maven plugin exposes the Native Image tracing agent and post-processing workflows through
Maven configuration and goals. Users can collect metadata from JVM test or application runs, then
merge or copy it into a metadata directory.

## 1. Agent enablement

The agent is disabled by default and can be enabled through plugin configuration or the
`-Dagent=true` command-line property. If enabled in the POM, `-Dagent=false` must disable it for a
single invocation.

## 2. Agent modes

The Maven configuration must support standard, direct, conditional, and disabled agent modes using
the shared agent mode contract in [§common/FS-common-libraries.3](../../../common/docs/functional-spec.md#3-native-image-tracing-agent). Conditional mode must support
user-code and extra filters, and direct mode must let users provide the raw agent command line when
they need full control.

## 3. Agent output

Agent output from tests must be stored under `target/native/agent-output/test`; agent output from
application runs must be stored under `target/native/agent-output/main` unless direct mode changes
the destination. Application agent runs are attached to the `exec-maven-plugin` execution named by
the native plugin's `<agentExecutionId>` configuration value; the default execution ID is
`java-agent` so existing POMs keep working. Generated test-agent arguments derived from project paths
must remain a single JVM argument when passed through Maven test runners, including when those paths
contain spaces. When adding that argument, Maven must preserve the test runner's existing `argLine`
options.
When Maven configures an instrumented test or application execution, normal build
output must report the Maven-managed agent output directory so users can find collected metadata
without debug logging, aligning with
[§root/GOAL-concise-actionable-output](../../../docs/spec/goals.md#goal-concise-actionable-output-build-output-is-concise-actionable-and-token-efficient).

### 3.1 Default access filter

Maven must materialize the built-in default access filter in a unique per-session directory under
`java.io.tmpdir` before configuring instrumented executions. The filter must remain outside the
project build directory so a later `clean` lifecycle phase cannot delete a path already injected
into test or application JVM arguments. Maven must create this directory only for enabled agent
configurations and must remove the filter and its directory when the Maven session ends.

## 4. Merge and copy

`native:merge-agent-files` must merge generated agent output through `native-image-configure`.
`native:metadata-copy` must copy or merge selected agent stages into the configured output
directory and honor disabled main/test stages.

### 4.1 Shared destination replacement

When sequential metadata-copy invocations share an output directory, `merge=false` must ignore
existing destination metadata and replace every Native Image metadata entry owned by the goal with
the current module's selected stages, while `merge=true` must include the existing destination as
an input and replace it with the combined result. The goal must generate into staging and update the
configured destination only after `native-image-configure` succeeds. If post-processing fails, the
configured destination must remain unchanged and the staging output must be removed. This preserves
the replacement and merge modes in
[§root/FS-tracing-agent.4](../../../docs/spec/functional/tracing-agent.md#4-merge-and-copy) and the
existing Maven parameter meanings required by
[§root/REQ-backwards-compatibility.2](../../../docs/spec/requirements.md#2-configuration-compatibility).

## 5. Agent example

Agent collection is enabled through `<agent>` configuration or `-Dagent=true`; post-processing is
invoked with `native:metadata-copy`. The default output location for test-stage output is
`target/native/agent-output/test` unless direct mode changes it.

```xml
<configuration>
    <agent>
        <enabled>true</enabled>
        <defaultMode>Standard</defaultMode>
        <metadataCopy>
            <merge>true</merge>
        </metadataCopy>
    </agent>
</configuration>
```

```bash
mvn -Pnative -Dagent=true test
mvn -Pnative native:metadata-copy
```
