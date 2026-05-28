# FS-maven-plugin: The Maven plugin wires Native Image behavior into Maven builds

The `native-maven-plugin` module provides a Maven plugin packaged as `maven-plugin`. Its mojos
adapt Native Image build, test, resource, metadata, and support workflows into Maven's lifecycle,
configuration, plugin descriptor, and repository model. This functional contract realizes
§GOAL-build-tool-native-image-workflows for Maven and depends on the shared behavior in
§FS-plugin-common-behavior, §FS-common-libraries, and §FS-native-tests-and-fixtures.

## 1. Plugin goal surface

The Maven plugin must expose Native Image workflows as Maven goals with lifecycle bindings,
parameter expressions, dependency resolution scopes, and descriptor metadata that fit Maven users.

### 1.1 Build goals

The `native:compile` goal must support command-line native image builds. It is intended for users
who explicitly invoke native compilation after Maven has prepared the project, and it may fork the
Maven lifecycle so packaged outputs and generated resources are available before the native image
is built. This goal is the Maven command-line entry point for §FS-plugin-common-behavior.2.

The `native:compile-no-fork` goal must support lifecycle-bound native image builds. It runs
without forking the Maven build, binds to the package phase, and is suitable for plugin executions
inside profiles or normal lifecycle use. It must perform the same native-image command
construction as `native:compile` once Maven project state is ready. This goal is the lifecycle
entry point for §FS-plugin-common-behavior.2.

The deprecated `native:build` goal may remain as a compatibility alias for existing users. It
must warn that the goal is deprecated and direct users toward `native:compile-no-fork`.

### 1.2 Test goal

The `native:test` goal must compile the Maven test classpath into a native test image and execute
that image unless test execution is skipped. It binds to the test phase, honors Maven test skip
settings and Native Build Tools test settings, and uses the shared native test contract in
§FS-plugin-common-behavior.3 and §FS-native-tests-and-fixtures.

### 1.3 Metadata and support goals

The `native:generateResourceConfig` goal must scan the main runtime classpath and generate Native
Image resource configuration for application builds. The `native:generateTestResourceConfig` goal
must do the same for the Maven test classpath so native test images see test resources. These
goals expose the resource generation part of §FS-plugin-common-behavior.4.

The `native:generateDynamicAccessMetadata` goal must derive dynamic access metadata from Native
Image build reports and make that metadata available to subsequent native image builds.
The `native:add-reachability-metadata` goal must resolve metadata from the configured reachability
metadata repository and add it to the native image configuration directories. These goals expose
the metadata parts of §FS-plugin-common-behavior.4.

The `native:merge-agent-files` goal must merge tracing-agent output with `native-image-configure`.
The `native:metadata-copy` goal must copy or merge selected tracing-agent output stages into a
configured metadata directory. These goals expose the shared agent post-processing behavior from
§FS-plugin-common-behavior.5 and §FS-common-libraries.4 through Maven.

The `native:list-libraries-missing-metadata` goal must report project dependencies that are not
covered by the configured reachability metadata repository. The `native:write-args-file` goal must
write the native-image arguments that Maven would pass, allowing users to inspect or reuse them.
These goals expose the missing-metadata and command-line compatibility workflows from
§FS-plugin-common-behavior.4 and §FS-plugin-common-behavior.6.

### 1.4 Lifecycle bindings

Goals that mutate generated project resources or build native images must bind to Maven lifecycle
phases only when that behavior is safe for normal profile usage. Utility goals such as
`metadata-copy`, `list-libraries-missing-metadata`, and `write-args-file` may remain unbound or
manual so users can invoke them intentionally.

## 2. Native image build behavior

Maven native image builds must translate Maven project state and plugin configuration into a
Native Image invocation equivalent to the Gradle command-line contract in
§FS-gradle-plugin.3.

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
contract in §FS-common-libraries.2.

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
command-line property from §FS-maven-plugin.3.2 (for example
`-DskipNativeBuild=...`) applies only when no explicit configuration is present. The exception is a
parameter intentionally modeled to let the property win for a single run, such as the agent toggle
in §FS-maven-plugin.5.1 where `-Dagent=false` disables an agent enabled in
the POM. This mirrors the Gradle precedence rule in
§FS-gradle-plugin.2.5.

## 4. Native tests

Maven native tests use the shared JUnit native support in §FS-native-tests-and-fixtures and expose
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
by §FS-native-tests-and-fixtures.5.

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
the shared agent mode contract in §FS-common-libraries.3. Conditional mode must
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
§FS-common-libraries.2.

### 6.2 Reachability metadata

`native:add-reachability-metadata` must resolve metadata for project dependencies from the
configured metadata repository, exclusions, and module-to-config-version overrides. It must add the
resulting configuration directory to the native image build without requiring users to manually
copy repository contents.

### 6.3 Missing metadata reports

`native:list-libraries-missing-metadata` must report project dependencies that do not appear to
have reachability metadata support and may create GitHub issues when issue creation is configured.
Its behavior must remain aligned with Gradle's missing-metadata task in
§FS-gradle-plugin.4.4.

### 6.4 Schema validation

Before using repository metadata, native image goals must validate metadata against the schema
expected by the discovered Native Image major version when schema validation is applicable.

## 7. Verification surface

The module's unit tests must cover mojos, configuration objects, command-line assembly, and shared
utility integration that can be tested locally. Functional tests must execute Maven sample
projects, issue reproducers, SBOM behavior, metadata repository integration, agent workflows,
resource generation, and native test execution scenarios through a seeded local Maven repository.
Those fixtures are owned by §AR-native-tests-and-fixtures.1.
