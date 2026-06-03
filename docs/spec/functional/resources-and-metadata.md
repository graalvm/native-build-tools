# FS-resources-and-metadata: Both plugins generate resource config and consume reachability metadata

Both plugins must expose resource configuration generation, reachability metadata repository
lookup, missing metadata reports, dynamic access metadata, and schema validation through their
own task or goal surface. The shared library behavior lives in §common/FS-common-libraries.2,
§common/FS-common-libraries.5, §common/FS-common-libraries.6, and §common/FS-common-libraries.7.
Gradle adapts through §gradle/FS-gradle-plugin.4; Maven adapts through §maven/FS-maven-plugin.6
plus the support goals in §maven/FS-maven-plugin.1.3.

Generated artifacts stay in the build-tool output tree (Gradle `build/native/`, Maven `target/`
under a `native/generated` subtree) unless the user explicitly requests a copy elsewhere.

## 1. Resource configuration

When resource autodetection is enabled for a binary or goal, the plugin must scan the binary's
runtime classpath (including JARs and directories), generate `resource-config.json` matching the
detected resources, and add the generated directory to the Native Image configuration file
directories used for that build.

If a classpath entry already contains `META-INF/native-image/.../resource-config.json` and the
caller has not asked to ignore existing config, the plugin must not duplicate resources from that
entry. Scanning, path normalization, and config generation belong to §common/FS-common-libraries.2.

| Build tool | Entry point | Output directory |
| --- | --- | --- |
| Gradle | `generateResourcesConfigFile`, `generate<Binary>ResourcesConfigFile` | `build/native/generated/<task>/` |
| Maven | `native:generateResourceConfig`, `native:generateTestResourceConfig` | `target/native/generated/generateResourceConfig/` |

## 2. Reachability metadata repository

Both plugins must resolve reachability metadata for the runtime classpath from the configured
repository URI, version, exclusions, and module-to-config-version overrides
(§GLOSS-reachability-metadata-repository). Resolved metadata must be exposed as a generated
directory passed to `native-image` as a configuration file directory, not unpacked into the
project source tree.

| Build tool | Configuration | Entry point |
| --- | --- | --- |
| Gradle | `graalvmNative.metadataRepository`, `binaries.<name>.excludeConfig` | `collectReachabilityMetadata` plus native compile tasks |
| Maven | `<metadataRepository>` | `native:add-reachability-metadata` plus native build goals |

## 3. Missing metadata reports

Both plugins must offer a report task or goal that inspects direct runtime dependencies, compares
them with the configured reachability metadata repository, and writes a JSON report of
dependencies that lack metadata coverage. The report must not modify the inputs of native compile
tasks. When issue-creation settings are configured, the report may open GitHub issues against the
configured repository.

| Build tool | Entry point |
| --- | --- |
| Gradle | `listLibrariesMissingMetadata` |
| Maven | `native:list-libraries-missing-metadata` |

## 4. Dynamic access metadata

Reachability information that Native Image cannot infer statically (§GLOSS-dynamic-access-metadata)
must be generated from a Native Image build report when a binary or goal is configured to emit
one. Generation uses the configured reachability metadata repository and the runtime classpath
graph; the resulting directory is added to the Native Image configuration file directories for
that build only.

| Build tool | Entry point |
| --- | --- |
| Gradle | `generateDynamicAccessMetadata`, `generate<Binary>DynamicAccessMetadata` |
| Maven | `native:generateDynamicAccessMetadata` |

## 5. Schema validation

Before passing repository metadata to `native-image`, plugins must validate metadata-bearing JSON
against the schema for its file type when the repository owns one and the build has enough Native
Image version information to choose a schema. Validation failures must report the offending file
and rule before any native image invocation. Shared validation behavior is
§common/FS-common-libraries.7.
