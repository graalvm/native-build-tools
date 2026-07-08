# FS-resources-and-metadata: Maven goals generate resources and consume reachability metadata

Maven projects use shared metadata and resource behavior through Maven goals. Generated Native
Image configuration should stay in Maven's `target` tree and feed later native builds
automatically.

## 1. Resource configuration goals

The plugin must generate resource configuration for main and test classpaths. Generation must
respect existing Native Image resource configuration and the shared classpath scanning behavior in
[§common/FS-common-libraries.2](../../../common/docs/functional-spec.md#2-resource-configuration).

For `native:generateTestResourceConfig`, autodetection for the current project must use Maven's
processed main and test output directories instead of raw resource source directories. This scans
the native-test runtime classpath while preserving resource filtering, exclusions, and resources
generated directly into either output.

## 2. Reachability metadata

`native:add-reachability-metadata` resolves metadata for project dependencies from the configured
metadata repository, exclusions, and module-to-config-version overrides. It must add the resulting
configuration directory to the native image build without requiring users to manually copy
repository contents. When no URI, version, or local path is pinned, the Maven default should follow
the repository-wide freshness goal in [§root/GOAL-fresh-metadata](../../../docs/spec/goals.md#goal-fresh-metadata-users-can-fetch-the-latest-graalvm-reachability-metadata).

## 3. Missing metadata reports

`native:list-libraries-missing-metadata` reports project dependencies that do not appear to have
reachability metadata support and may create GitHub issues when issue creation is configured. Its
behavior must remain aligned with [§root/FS-resources-and-metadata](../../../docs/spec/functional/resources-and-metadata.md#fs-resources-and-metadata-both-plugins-generate-resource-config-and-consume-reachability-metadata).

## 4. Schema validation

Before using repository metadata, native image goals must validate metadata against the schema
expected by the discovered Native Image major version when schema validation is applicable.

## 5. Resource and metadata entry points

Resource generation is exposed through `native:generateResourceConfig` and
`native:generateTestResourceConfig`. Reachability metadata is configured with
`<metadataRepository>` and consumed by native build goals through the selected metadata
directories.

```xml
<execution>
    <id>build-native</id>
    <goals>
        <goal>generateResourceConfig</goal>
        <goal>compile-no-fork</goal>
    </goals>
    <phase>package</phase>
</execution>
```

```xml
<metadataRepository>
    <enabled>true</enabled>
    <version>1.0-M1</version>
</metadataRepository>
```
