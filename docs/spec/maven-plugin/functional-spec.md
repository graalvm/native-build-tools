# FS-002-maven-plugin-native-image-workflow: The Maven plugin wires Native Image behavior into Maven builds

The `native-maven-plugin` module provides a Maven plugin packaged as `maven-plugin`. Its mojos
adapt Native Image build, test, resource, metadata, and support workflows into Maven's lifecycle,
configuration, plugin descriptor, and repository model. This functional contract realizes
§GOAL-001-build-tool-native-image-workflows for Maven and depends on the shared behavior in
§FS-003-metadata-and-resource-workflows and §FS-004-native-test-execution.

## 1. Plugin goal surface

The Maven plugin must expose Native Image workflows as Maven goals with lifecycle bindings,
parameter expressions, dependency resolution scopes, and descriptor metadata that fit Maven users.

### 1.1 Build goals

The plugin must provide `native:compile` for command-line native image builds and
`native:compile-no-fork` for lifecycle-bound native image builds. The deprecated `native:build`
goal may remain as a compatibility alias, but it must direct users toward `compile-no-fork`.

### 1.2 Test goal

The plugin must provide `native:test` for compiling and optionally executing native test images.
The detailed native test contract is §FS-004-native-test-execution.

### 1.3 Metadata and support goals

The plugin must provide goals for generated resource configuration, generated test resource
configuration, dynamic access metadata, reachability metadata addition, tracing-agent merge,
metadata copy, missing metadata listing, and argument-file writing.

### 1.4 Lifecycle bindings

Goals that mutate generated project resources or build native images must bind to Maven lifecycle
phases only when that behavior is safe for normal profile usage. Utility goals such as
`metadata-copy`, `list-libraries-missing-metadata`, and `write-args-file` may remain unbound or
manual so users can invoke them intentionally.

## 2. Native image build behavior

Maven native image builds must translate Maven project state and plugin configuration into a
Native Image invocation equivalent to the Gradle command-line contract in
§FS-001-gradle-plugin-native-image-workflow.3.

### 2.1 Main class discovery

When `mainClass` is not configured directly, `native:compile-no-fork` must inspect common Maven
packaging plugins for a main class in this order: Maven Shade Plugin transformer configuration,
Maven Assembly Plugin archive manifest configuration, then Maven JAR Plugin archive manifest
configuration. Values must be evaluated through Maven expression evaluation before use.

### 2.2 Build skipping

`skipNativeBuild` must skip native image generation. `skipNativeBuildForPom` must skip native image
generation for projects packaged as `pom` when that parameter is enabled.

### 2.3 Classpath and scopes

Application native image builds must include compile, runtime, and combined compile-plus-runtime
dependencies unless users provide an explicit classpath. Exclusions must remove selected
artifacts from native-image compilation without changing the Maven project dependency graph.

### 2.4 Generated resource configuration

Before building, the plugin must add generated resource configuration to the native image
arguments when resource autodetection is configured. That generation uses the shared resource
contract in §FS-003-metadata-and-resource-workflows.2.

### 2.5 Dynamic access metadata

When configured build arguments ask Native Image to emit a build report, the build goal must run
`generateDynamicAccessMetadata` before native image compilation and make the resulting metadata
available to the build.

### 2.6 Base SBOM

When base SBOM generation is supported by the discovered Oracle GraalVM Native Image version and
the user has not disabled it, the build goal must attempt to generate a base SBOM. Failure to
generate that auxiliary SBOM must warn and fall back to Maven's regular SBOM behavior rather than
failing an otherwise valid native image build.

### 2.7 Argument files

`native:write-args-file` must write an argument file using the same argument conversion semantics
as native image compilation. It must be useful for inspecting or reusing the exact native-image
arguments that Maven would pass.

## 3. Configuration model

The plugin must map Maven XML configuration and system properties into one native image option
model.

### 3.1 Native Image options

The plugin must support image name, main class, build args, runtime args, debug, verbose, fallback,
shared-library output, quick build, argument-file usage, classpath, classes directory, dependency
exclusions, environment variables, system properties, JVM args, configuration file directories,
metadata repository settings, required Native Image version, and agent configuration.

### 3.2 Command-line properties

Configuration values documented as Maven command-line properties must be overridable through
`-D...` properties for temporary runs. The property path must feed the same option state as XML
configuration so behavior does not diverge by configuration source.

### 3.3 Parent POM merging

Configuration that Maven natively supports as mergeable, such as `<buildArgs>`, must preserve
Maven's parent/child merge behavior. Child projects must be able to append to parent build
arguments when they use Maven's `combine.children="append"` convention.

### 3.4 Toolchain and executable lookup

The plugin must locate a Native Image executable using Maven toolchains when appropriate and
environment/path fallbacks otherwise. When toolchain enforcement is enabled, failing to find a
toolchain-provided Native Image executable must fail clearly.

### 3.5 Override precedence

Maven's standard parameter binding decides precedence between configuration sources. When a
parameter is set in `<configuration>` XML, that explicit value takes precedence; the matching
command-line property from §FS-002-maven-plugin-native-image-workflow.3.2 (for example
`-DskipNativeBuild=...`) applies only when no explicit configuration is present. The exception is a
parameter intentionally modeled to let the property win for a single run, such as the agent toggle
in §FS-002-maven-plugin-native-image-workflow.5.1 where `-Dagent=false` disables an agent enabled in
the POM. This mirrors the Gradle precedence rule in
§FS-001-gradle-plugin-native-image-workflow.2.5.

## 4. Native tests

Maven native tests use the shared JUnit native support in §FS-004-native-test-execution and expose
it through Maven's test lifecycle.

### 4.1 Test classpath

`native:test` must include compile, runtime, test, compile-plus-runtime, and provided-scope
dependencies needed by the test image. It must add compiled test classes, test resources, plugin
artifacts relevant to Native Build Tools and JUnit, and inferred `junit-platform-native`
dependencies.

### 4.2 Test discovery preconditions

If `skipTests` or `skipNativeTests` is set, the goal must skip native tests. If no test classes are
present, it must skip native tests. If test identifier files are missing, `failNoTests` determines
whether the goal fails or skips.

### 4.3 Launcher and feature selection

By default, `native:test` must build the image with
`org.graalvm.junit.platform.NativeImageJUnitLauncher` and the `JUnitPlatformFeature`. If Native
Image compatibility mode is enabled, it must use the original JUnit ConsoleLauncher path described
by §FS-004-native-test-execution.5.

### 4.4 Test execution

After building the native test image, `native:test` must run it unless `skipTestExecution` is set.
Runtime arguments configured for the test goal must be passed to the native test executable.

## 5. Agent and metadata copy workflows

The Maven plugin must expose the Native Image tracing agent and post-processing workflows through
Maven configuration and goals.

### 5.1 Agent enablement

The agent is disabled by default and can be enabled through plugin configuration or the
`-Dagent=true` command-line property. If enabled in the POM, `-Dagent=false` must disable it for a
single invocation.

### 5.2 Agent modes

The Maven configuration must support standard, direct, conditional, and disabled agent modes using
the shared agent mode contract in §FS-003-metadata-and-resource-workflows.3. Conditional mode must
support user-code and extra filters, and direct mode must let users provide the raw agent command
line when they need full control.

### 5.3 Agent output

Agent output from tests must be stored under `target/native/agent-output/test`; agent output from
application runs must be stored under `target/native/agent-output/main` unless direct mode changes
the destination.

### 5.4 Merge and copy

`native:merge-agent-files` must merge generated agent output through `native-image-configure`.
`native:metadata-copy` must copy or merge selected agent stages into the configured output
directory and honor disabled main/test stages.

## 6. Resource and reachability metadata workflows

Maven projects use shared metadata and resource behavior through Maven goals.

### 6.1 Resource configuration goals

The plugin must generate resource configuration for main and test classpaths. Generation must
respect existing Native Image resource configuration and the shared classpath scanning behavior in
§FS-003-metadata-and-resource-workflows.2.

### 6.2 Reachability metadata

`native:add-reachability-metadata` must resolve metadata for project dependencies from the
configured metadata repository, exclusions, and module-to-config-version overrides. It must add the
resulting configuration directory to the native image build without requiring users to manually
copy repository contents.

### 6.3 Missing metadata reports

`native:list-libraries-missing-metadata` must report project dependencies that do not appear to
have reachability metadata support and may create GitHub issues when issue creation is configured.
Its behavior must remain aligned with Gradle's missing-metadata task in
§FS-001-gradle-plugin-native-image-workflow.4.4.

### 6.4 Schema validation

Before using repository metadata, native image goals must validate metadata against the schema
expected by the discovered Native Image major version when schema validation is applicable.

## 7. Verification surface

The module's unit tests must cover mojos, configuration objects, command-line assembly, and shared
utility integration that can be tested locally. Functional tests must execute Maven sample
projects, issue reproducers, SBOM behavior, metadata repository integration, agent workflows,
resource generation, and native test execution scenarios through a seeded local Maven repository.
Those fixtures are owned by §AR-004-samples-and-functional-fixtures.
