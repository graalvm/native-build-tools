# FS-003-metadata-and-resource-workflows: The plugins support resource config, agent metadata, and reachability metadata workflows

Native Build Tools must help users supply the configuration that Native Image needs for resources,
reflection, dynamic access, and third-party library reachability metadata. The behavior is shared
where possible and adapted into Gradle tasks and Maven mojos where build-tool integration differs.
This functional contract realizes §GOAL-002-shared-native-image-behavior-stays-consistent.

## 1. Shared Native Image utilities

Common utility behavior must keep Gradle and Maven command-line handling consistent.

### 1.1 Argument escaping

Arguments passed to Native Image must be escaped consistently when they are written to an argument
file or passed through a build-tool process API. Escaping rules must preserve whitespace, quotes,
backslashes, and platform path semantics.

### 1.2 Argument files

Shared conversion utilities must be able to write a Native Image argument file and return the
corresponding `@...` argument. Build-tool plugins must use those utilities instead of implementing
separate argument-file writers.

### 1.3 Version parsing

Shared version utilities must parse Native Image and JDK version strings well enough to select
major-version-specific behavior such as schema validation and Native Image option spelling.

### 1.4 Native Image configuration names

Common constants must define Native Image configuration file names and metadata directory names so
Gradle, Maven, and shared tests agree on generated file layout.

## 2. Resource configuration

Resource detection must help users include non-classpath resources in native images without
requiring hand-written `resource-config.json` for common project shapes.

### 2.1 Classpath entry analysis

The analyzer must inspect classpath directories and JAR files, discover resources that should be
eligible for Native Image resource configuration, and normalize paths with portable separators.

### 2.2 Existing Native Image config

When an analyzed classpath entry already contains `META-INF/native-image/.../resource-config.json`
and existing configuration should be respected, the analyzer must not duplicate that entry's
resources into generated configuration.

### 2.3 Ignoring existing config

When callers explicitly request that existing Native Image resource configuration be ignored, the
analyzer may still enumerate resources from entries that contain Native Image configuration.

### 2.4 Resource output

Generated resource configuration must use Native Image's resource config shape and must be written
to a directory that the corresponding Gradle task or Maven goal passes as a configuration file
directory.

## 3. Native Image tracing agent

Agent behavior is shared because Gradle and Maven need the same mode semantics even though they
attach the agent to different execution APIs.

### 3.1 Standard mode

Standard mode must invoke `native-image-configure` or agent output handling with regular input and
output directories, producing metadata without conditionalization.

### 3.2 Conditional mode

Conditional mode must support user-code filters and optional extra filters so collected metadata
can be conditionalized around application code. When a build-tool integration exposes parallel or
partial config behavior, it must still use the conditional mode contract for merge arguments.

### 3.3 Direct mode

Direct mode must let users provide native agent options directly. Build-tool integrations may
still substitute well-known placeholders such as `{output_dir}`, but direct mode must otherwise
avoid interpreting the user's option list.

### 3.4 Disabled mode

Disabled mode must produce no agent configuration arguments. It is the explicit state for users
who want a configured plugin but no agent instrumentation in a particular execution.

### 3.5 Advanced filters

Caller filters, access filters, built-in caller filters, built-in heuristic filters,
experimental predefined classes, unsafe allocation tracing, and reflection metadata tracking must
be represented in the shared agent option model so both product plugins expose the same behavior.

## 4. Agent metadata post-processing

Collected agent output must be usable without users manually invoking Native Image helper tools.

### 4.1 Merge inputs and outputs

Merge behavior must accept one or more input directories and one or more output directories. The
result must preserve Native Image metadata file names and write merged output to the requested
destination.

### 4.2 Tool discovery

Merge behavior must locate the `native-image-configure` helper from the same Native Image
installation used for the build when possible, falling back through the same executable discovery
path used by the product plugin.

### 4.3 Copy behavior

Copy behavior must be able to either replace metadata in the destination or merge with existing
destination metadata, depending on user configuration.

## 5. Reachability metadata repository

Reachability metadata repository support must let both product plugins consume the GraalVM
Reachability Metadata Repository without embedding build-tool-specific lookup logic.

### 5.1 Repository sources

The repository layer must support metadata from a configured URI or local directory. Product
plugins decide how that source is downloaded or unpacked, but query behavior belongs in common
code.

### 5.2 Module and artifact indexes

The repository layer must parse module and artifact indexes that map dependency coordinates and
versions to metadata directories. It must honor entries marked as not applicable to Native Image.

### 5.3 Version selection

Given a dependency coordinate and version, the repository must select the most appropriate metadata
directory according to repository index data, configured module-to-config-version overrides,
default mappings, and excluded modules.

### 5.4 Query results

Queries must distinguish supported dependencies, dependencies excluded by user configuration,
dependencies explicitly marked as not for Native Image, and dependencies with no known metadata.

### 5.5 Repository output

Resolved metadata must be copied or exposed through a build directory layout that product plugins
can pass to Native Image as a configuration file directory.

## 6. Missing metadata reporting

Missing metadata reporting must identify libraries where users are likely to need additional
reachability metadata.

### 6.1 Dependency scope

Reports should focus on direct runtime dependencies by default, avoiding noise from build-tool
implementation dependencies and transitive details that users may not control.

### 6.2 Report contents

Reports must include enough coordinate and repository-status information for users or automation
to decide whether to request metadata support.

### 6.3 Issue creation

When issue creation is enabled, common support must format issue requests against the configured
GitHub repository and API URL. Product plugins supply credentials and project identity.

## 7. Schema validation

Metadata-oriented JSON must be validated when the repository owns a schema for the file type and
the build has enough Native Image version information to select the correct schema.

### 7.1 Native Image version dependency

Schema validation may vary by Native Image major version. Product plugins must supply the
discovered version so common validation can select the expected schema.

### 7.2 Failure behavior

Invalid metadata that would be passed to Native Image must fail early with a validation error
rather than producing a later, less actionable native-image failure.

## 8. Verification surface

Common utility and reachability metadata modules must have unit tests for argument conversion,
resource scanning, agent mode command lines, repository index parsing, metadata lookup, missing
metadata support, schema validation, and Native Image version behavior. Product plugin functional
tests cover these shared behaviors through Gradle and Maven sample projects.
