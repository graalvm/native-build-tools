# FS-resources-and-metadata: Both plugins generate resource config and consume reachability metadata

Both plugins must expose resource configuration generation, reachability metadata repository
lookup, missing metadata reports, dynamic access metadata, and schema validation through their
own task or goal surface. The shared library behavior lives in [§common/FS-common-libraries.2](../../../common/docs/functional-spec.md#2-resource-configuration),
[§common/FS-common-libraries.5](../../../common/docs/functional-spec.md#5-reachability-metadata-repository), [§common/FS-common-libraries.6](../../../common/docs/functional-spec.md#6-missing-metadata-reporting), and [§common/FS-common-libraries.7](../../../common/docs/functional-spec.md#7-schema-validation).
Gradle adapts through [§gradle/FS-resources-and-metadata](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#fs-resources-and-metadata-gradle-tasks-generate-resources-and-consume-reachability-metadata); Maven adapts through [§maven/FS-resources-and-metadata](../../../native-maven-plugin/docs/functional/resources-and-metadata.md#fs-resources-and-metadata-maven-goals-generate-resources-and-consume-reachability-metadata)
plus the support goals in [§maven/FS-goal-surface.3](../../../native-maven-plugin/docs/functional/goal-surface.md#3-metadata-and-support-goals). Repository defaults realize
[§GOAL-fresh-metadata](../goals.md#goal-fresh-metadata-users-can-fetch-the-latest-graalvm-reachability-metadata).

Generated artifacts stay in the build-tool output tree unless the user explicitly requests a copy
elsewhere. Exact task names, goal names, output paths, and configuration names belong to the
Gradle and Maven functional specs cited above.

## 1. Resource configuration

When resource autodetection is enabled for a binary or goal, the plugin must scan the binary's
runtime classpath (including JARs and directories), generate `resource-config.json` matching the
detected resources, and add the generated directory to the Native Image configuration file
directories used for that build.

If a classpath entry already contains `META-INF/native-image/.../resource-config.json` and the
caller has not asked to ignore existing config, the plugin must not duplicate resources from that
entry. Scanning, path normalization, and config generation belong to [§common/FS-common-libraries.2](../../../common/docs/functional-spec.md#2-resource-configuration).

Plugin-specific entry points and output paths are specified by
[§gradle/FS-resources-and-metadata.1](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#1-resource-autodetection), [§gradle/FS-resources-and-metadata.2](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#2-generated-resource-configuration), and
[§maven/FS-resources-and-metadata.1](../../../native-maven-plugin/docs/functional/resources-and-metadata.md#1-resource-configuration-goals).

## 2. Reachability metadata repository

Both plugins must resolve reachability metadata for the runtime classpath from the configured
repository URI, version, exclusions, and module-to-config-version overrides
([§GLOSS-reachability-metadata-repository](../glossary.md#gloss-reachability-metadata-repository-graalvm-reachability-metadata-repository)). Resolved metadata must be exposed as a generated
directory passed to `native-image` as a configuration file directory, not unpacked into the
project source tree.

When the user has not pinned a URI, version, or local repository path, the default repository
selection must prefer the latest compatible official metadata release available to the plugin.

Plugin-specific configuration and entry points are specified by
[§gradle/FS-resources-and-metadata.3](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#3-reachability-metadata-collection) and [§maven/FS-resources-and-metadata.2](../../../native-maven-plugin/docs/functional/resources-and-metadata.md#2-reachability-metadata).

## 3. Missing metadata reports

Both plugins must offer a report task or goal that inspects direct runtime dependencies, compares
them with the configured reachability metadata repository, and writes a JSON report of
dependencies that lack metadata coverage. The report must not modify the inputs of native compile
tasks. When issue-creation settings are configured, the report may open GitHub issues against the
configured repository.

Plugin-specific report entry points are specified by [§gradle/FS-resources-and-metadata.4](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#4-missing-metadata-reports)
and [§maven/FS-resources-and-metadata.3](../../../native-maven-plugin/docs/functional/resources-and-metadata.md#3-missing-metadata-reports).

## 4. Dynamic access metadata

Reachability information that Native Image cannot infer statically ([§GLOSS-dynamic-access-metadata](../glossary.md#gloss-dynamic-access-metadata-dynamic-access-metadata))
must be generated from a Native Image build report when a binary or goal is configured to emit
one. Generation uses the configured reachability metadata repository and the runtime classpath
graph; the resulting directory is added to the Native Image configuration file directories for
that build only.

Plugin-specific generation entry points are specified by
[§gradle/FS-resources-and-metadata.5](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#5-dynamic-access-metadata) and [§maven/FS-native-builds.5](../../../native-maven-plugin/docs/functional/native-image-builds.md#5-dynamic-access-metadata).

## 5. Schema validation

Before passing repository metadata to `native-image`, plugins must validate metadata-bearing JSON
against the schema for its file type when the repository owns one and the build has enough Native
Image version information to choose a schema. Validation failures must report the offending file
and rule before any native image invocation. Shared validation behavior is
[§common/FS-common-libraries.7](../../../common/docs/functional-spec.md#7-schema-validation).
