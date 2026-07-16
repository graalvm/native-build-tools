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
contain spaces.

## 4. Merge and copy

`native:merge-agent-files` must merge generated agent output through `native-image-configure`.
`native:metadata-copy` must copy or merge selected agent stages into the configured output
directory and honor disabled main/test stages.

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
