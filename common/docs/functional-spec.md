# FS-common-libraries: Common libraries provide shared Native Image utilities and metadata workflows

Native Build Tools must help users supply the configuration that Native Image needs for resources,
reflection, dynamic access, and third-party reachability metadata. The `common/` modules own the
build-tool-neutral behavior that Gradle and Maven adapt into tasks, goals, and configuration
models. This supports §root/FS-plugin-common-behavior and realizes
§root/GOAL-plugin-parity through §GOAL-common-libraries-shared-native-image-semantics and
§REQ-common-libraries-stable-shared-semantics.

## At a Glance

| Shared capability | What common code owns | Plugin adaptation |
| --- | --- | --- |
| Argument handling | escaping, argument-file conversion, version parsing, constants | Gradle tasks and Maven goals decide which options are present |
| Resource configuration | classpath/JAR scanning and `resource-config.json` shape | plugins wire generated output into binary/goal config directories |
| Tracing agent | standard, conditional, direct, disabled modes and merge/copy primitives | plugins attach the agent to Gradle or Maven execution APIs |
| Reachability metadata | repository parsing, version selection, query result classification | plugins download/unpack/copy selected metadata for native-image |
| Missing metadata reports | dependency classification and report shape | plugins expose task/goal entry points and optional issue creation |
| Schema validation | schema lookup and Native Image major-version compatibility | plugins decide when validation runs before native-image invocation |

Common code answers Native Image questions. Gradle and Maven code answer build-tool questions such
as task inputs, scopes, provider wiring, lifecycle bindings, and diagnostics. This boundary is
required by §REQ-common-libraries-no-build-tool-api-dependencies.

## 1. Shared Native Image utilities

Common utilities keep Gradle and Maven command-line handling consistent. They must preserve
whitespace, quotes, backslashes, and platform paths when escaping arguments; write Native Image
argument files and return the corresponding `@...` argument (§root/GLOSS-argument-file); parse Native
Image and JDK versions well enough to choose version-specific behavior; and centralize Native
Image configuration file names and metadata directory names used by plugins and tests.

## 2. Resource configuration

Resource detection helps users include non-classpath resources without hand-written
`resource-config.json` for common project shapes. The analyzer must inspect classpath directories
and JARs, normalize paths with portable separators, respect existing
`META-INF/native-image/.../resource-config.json` files unless the caller asks to ignore them, and
write Native Image resource configuration into the directory passed by the Gradle task or Maven
goal.

Product-specific task and goal entry points are specified by §gradle/FS-gradle-plugin.4.1,
§gradle/FS-gradle-plugin.4.2, and §maven/FS-maven-plugin.6.1.

## 3. Native Image tracing agent

Agent behavior is shared because Gradle and Maven need the same mode semantics even though they
attach the agent to different execution APIs. The agent is defined in §root/GLOSS-tracing-agent.

### 3.1 Agent modes

The shared model must cover standard collection, conditional metadata with user-code and extra
filters, direct user-supplied agent options, and disabled instrumentation. Direct mode may
substitute well-known placeholders such as `{output_dir}`, but must otherwise preserve the user's
option list. Advanced options such as caller filters, access filters, predefined classes, unsafe
allocation tracing, and reflection metadata tracking must be represented once so both plugins
expose the same mode semantics.

### 3.2 Plugin invocation and output

Plugin-specific enablement, instrumentation hooks, and output locations are specified by
§gradle/FS-gradle-plugin.5 and §maven/FS-maven-plugin.5. Both plugins may adapt the exact task or
process hook, but they must feed the same shared mode model into `native-image-agent`.

## 4. Agent metadata post-processing

Collected agent output must be usable without manual Native Image helper calls. Common merge
behavior accepts one or more input and output directories, preserves Native Image metadata file
names, and locates `native-image-configure` from the same Native Image installation used for the
build when possible. Copy behavior must either replace destination metadata or merge with it,
depending on user configuration.

Plugin-specific merge and copy entry points are specified by §gradle/FS-gradle-plugin.5.5 and
§maven/FS-maven-plugin.5.4.

## 5. Reachability metadata repository

Reachability metadata repository support lets both product plugins consume the GraalVM
Reachability Metadata Repository without build-tool-specific lookup logic. The repository is
defined in §root/GLOSS-reachability-metadata-repository.

### 5.1 Repository lookup

The common repository layer must support configured URIs and local directories, parse module and
artifact indexes, honor entries marked as not applicable to Native Image, and select the best
metadata directory using index data, module-to-config-version overrides, default mappings, and
exclusions. Queries must classify dependencies as supported, excluded, not-for-Native-Image, or
missing so plugin diagnostics and reports do not duplicate repository rules.

### 5.2 Plugin entry points and outputs

Product-specific repository resolution entry points are specified by §gradle/FS-gradle-plugin.4.3
and §maven/FS-maven-plugin.6.2. In every adaptation, resolved metadata must be exposed through a
generated build directory that the plugin can pass to Native Image as a configuration file
directory.

## 6. Missing metadata reporting

Missing metadata reporting must identify libraries where users are likely to need additional
reachability metadata. Reports should focus on direct runtime dependencies by default, include
enough coordinate and repository-status information for users or automation to decide whether to
request support, and format issue requests against the configured GitHub repository and API URL
when issue creation is enabled. Product plugins supply credentials and project identity.

Product-specific report entry points are specified by §gradle/FS-gradle-plugin.4.4 and
§maven/FS-maven-plugin.6.3.

## 7. Schema validation

Metadata-oriented JSON must be validated when the repository owns a schema for the file type and
the build has enough Native Image version information to choose the correct schema. Invalid
metadata that would be passed to Native Image must fail early with a validation error rather than
produce a later, less actionable native-image failure.
§REQ-common-libraries-version-and-schema-compatibility.

## 8. Verification surface

Common utility and reachability metadata modules must have unit tests for argument conversion,
resource scanning, agent mode command lines, repository index parsing, metadata lookup, missing
metadata support, schema validation, and Native Image version behavior. Product plugin functional
tests cover these shared behaviors through Gradle and Maven sample projects as part of
§root/FS-plugin-common-behavior.2 and §E2E-common-library-tests.
