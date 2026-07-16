# FS-resources-and-metadata: Gradle tasks generate resources and consume reachability metadata

The plugin exposes the shared metadata and resource contracts in
[§root/FS-resources-and-metadata](../../../docs/spec/functional/resources-and-metadata.md#fs-resources-and-metadata-both-plugins-generate-resource-config-and-consume-reachability-metadata) and [§common/FS-common-libraries](../../../common/docs/functional-spec.md#fs-common-libraries-common-libraries-provide-shared-native-image-utilities-and-metadata-workflows) through Gradle DSL and tasks.

## 1. Resource autodetection

When a binary enables resource autodetection, the plugin must scan that binary's runtime classpath
and generate `resource-config.json`. If a classpath entry already contains Native Image resource
configuration and existing configuration should not be ignored, the plugin must not duplicate
resources from that entry.

The main binary resource task is `generateResourcesConfigFile`. Custom binaries receive
`generate<Binary>ResourcesConfigFile` tasks. The test binary's generated resource task contributes
to `nativeTestCompile`.

## 2. Generated resource configuration

Generated resource configuration must be placed under the configured generated-resources directory
and added to the binary's configuration file directories so the compile task consumes it
automatically.

## 3. Reachability metadata collection

`collectReachabilityMetadata` resolves metadata for the runtime classpath from the configured
metadata repository URI, version, exclusions, and module-to-config-version overrides. Its output
directory must be consumable by native compile tasks and must contain only metadata selected for
the binary's dependency graph. When no URI or version is pinned, the Gradle default should follow
the repository-wide freshness goal in [§root/GOAL-fresh-metadata](../../../docs/spec/goals.md#goal-fresh-metadata-users-can-fetch-the-latest-graalvm-reachability-metadata).
Normal Gradle output must identify the selected metadata repository version
or, when a version is not known for a local path or arbitrary URL, the selected repository source.
This keeps important integration state visible without flooding build logs, as required by
[§root/GOAL-concise-actionable-output](../../../docs/spec/goals.md#goal-concise-actionable-output-build-output-is-concise-actionable-and-token-efficient).

## 4. Missing metadata reports

`listLibrariesMissingMetadata` inspects direct runtime dependencies, compares them with the
configured reachability metadata repository, writes a JSON report, and may create GitHub issues
when issue-creation settings are supplied. The task reports missing metadata without modifying the
native compile task inputs.

## 5. Dynamic access metadata

When a binary is configured to emit a Native Image build report, the plugin must generate
dynamic-access metadata before invoking Native Image. The task uses the configured reachability
metadata repository and runtime classpath graph, then makes the result available as generated
Native Image configuration. The metadata is defined in [§root/GLOSS-dynamic-access-metadata](../../../docs/spec/glossary.md#gloss-dynamic-access-metadata-dynamic-access-metadata).

The main binary task is `generateDynamicAccessMetadata`; custom binaries receive
`generate<Binary>DynamicAccessMetadata`. The task output is added to the binary's classpath only
when the binary requests a Native Image build report.

## 6. Metadata examples

Repository metadata is configured through `graalvmNative.metadataRepository`; exclusions are
configured on binaries through `excludeConfig`. `nativeCompile` consumes selected repository
metadata, while `listLibrariesMissingMetadata` reports uncovered dependencies separately.

```groovy
graalvmNative {
    metadataRepository {
        enabled = true
    }
    binaries.all {
        excludeConfig.put("com.example:library:1.0", [".*"])
    }
}
```

```bash
./gradlew nativeCompile
./gradlew listLibrariesMissingMetadata
```
