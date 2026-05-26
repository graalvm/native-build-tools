# FS-001-gradle-plugin-native-image-workflow: The Gradle plugin wires Native Image behavior into Gradle builds

The `native-gradle-plugin` module provides the Gradle plugin identified as
`org.graalvm.buildtools.native`. Applying that plugin gives a Gradle project a GraalVM extension,
native-image related tasks, command-line providers, metadata tasks, and test integration that are
expressed using Gradle's plugin, task, provider, and configuration-cache conventions. This
functional contract realizes §GOAL-001-build-tool-native-image-workflows for Gradle and depends on
the shared behavior in §FS-003-metadata-and-resource-workflows and §FS-004-native-test-execution.

## 1. Plugin activation and Gradle model

The plugin must register its behavior without replacing Gradle's Java, Application, Java Library,
or testing models. It supplements those plugins by adding Native Image-specific state and tasks.

### 1.1 Plugin identity

The plugin ID is `org.graalvm.buildtools.native`. A project that applies the plugin but has not
yet applied a Java plugin must not eagerly create Java-dependent native tasks; Java-dependent task
registration starts when the Java plugin is present.

### 1.2 Extension surface

The plugin must expose a `graalvmNative` extension. The extension owns:

- a named `binaries` container for native image binaries;
- shared Native Image options applied to binaries;
- toolchain detection controls;
- generated-resource and argument-file behavior;
- reachability metadata repository configuration;
- native test support enablement;
- Native Image tracing-agent configuration and metadata-copy settings.

The extension is the durable Gradle DSL surface. Command-line overrides are allowed for
experimentation, but reproducible build configuration belongs in the DSL.

### 1.3 Default binaries

For Java application projects, the plugin must create a `main` binary whose `mainClass` convention
comes from the Gradle `application` extension when available. For Java library projects, the main
binary defaults to shared-library output. The plugin must also create a `test` binary connected to
the default `test` task and the `test` source set.

### 1.4 Custom binaries

Additional entries in the `binaries` container must create matching native compile and run tasks.
Custom binaries use the same option model as `main` and `test`; task names are derived from the
binary name so that the Gradle task graph remains predictable.

## 2. Native image task behavior

Native image build and run tasks are the Gradle execution surface for the plugin.

### 2.1 Compile tasks

The `main` binary must be compiled by `nativeCompile`; the `test` binary must be compiled by
`nativeTestCompile`; custom binaries must receive derived `native<Binary>Compile` tasks. Compile
tasks must declare Gradle inputs and outputs for the selected options, output directory, optional
classpath JAR, argument-file behavior, metadata repository state, and test-list directory when
applicable.

### 2.2 Run tasks

The `main` binary must be executable through `nativeRun`. Custom binaries must receive a derived
native run task, and test binaries must be executable through `nativeTest` as specified by
§FS-004-native-test-execution. Run tasks must consume the compile task output file and pass runtime
arguments from the corresponding binary configuration.

### 2.3 Deprecated task aliases

The plugin must keep compatibility aliases for deprecated task names where they still exist, but
those aliases should depend on the replacement task and warn users to use the current name.

### 2.4 Command-line overrides

Compile tasks must expose Gradle task options for common experimentation switches: image name,
main class, debug, verbose, fallback, quick build, rich output, PGO instrumentation, build args,
forced build args, fat JAR mode, system properties, environment variables, JVM args, and forced
JVM args. Overrides must update the same option objects used by the DSL so command-line and DSL
behavior flow through one command-line assembly path.

## 3. Native Image invocation

Native Image invocation is responsible for locating the executable, validating requested
preconditions, assembling command-line arguments, and executing `native-image`.

### 3.1 Executable discovery

Compile and metadata tasks must find the `native-image` executable from the configured Gradle Java
launcher/toolchain when toolchain detection is enabled, or from GraalVM/JDK environment and path
fallbacks when it is not. Failure diagnostics must explain which lookup path was attempted.

### 3.2 Version and schema gates

When users configure a required Native Image version, the compile task must check the discovered
version before building. When reachability metadata from a repository is enabled, the task must
validate repository metadata against the schema expected by the discovered Native Image major
version before passing that metadata to `native-image`.

### 3.3 Command-line construction

The command line must combine classpath, module path where applicable, output name, main class,
boolean image flags, build arguments, JVM arguments, system properties, environment variables,
configuration file directories, resource configuration output, reachability metadata output, layer
options, and PGO options. Shared command-line escaping and argument-file conversion must come from
common utilities rather than Gradle-only string handling. This keeps Gradle aligned with the Maven
contract in §FS-002-maven-plugin-native-image-workflow.6.2.

### 3.4 Argument files

The plugin must support Native Image argument files for command lines that should not be passed as
plain process arguments. Argument-file generation must preserve argument semantics and use paths
relative to the selected working directory where that is required by Native Image.

### 3.5 Classpath JAR and artifact analysis

When configured to use a classpath JAR, the compile task must receive the generated JAR rather than
an exploded classpath. The plugin may analyze runtime classpath JARs through Gradle artifact
transforms to discover packages and resource behavior, but the transform output must remain an
internal implementation detail.

### 3.6 Parallel native builds

The plugin must limit concurrent Native Image builds through a Gradle build service. The limit
must be configurable through the `org.graalvm.buildtools.max.parallel.builds` Gradle property or
the `GRAALVM_BUILDTOOLS_MAX_PARALLEL_BUILDS` environment variable, with a conservative default
based on available processors.

## 4. Resources and reachability metadata

Gradle projects use the shared metadata and resource contracts in
§FS-003-metadata-and-resource-workflows through Gradle tasks and extension settings.

### 4.1 Resource autodetection

When a binary enables resource autodetection, the plugin must scan that binary's runtime classpath
and generate a Native Image `resource-config.json` file. If an analyzed classpath entry already
contains Native Image resource configuration and existing configuration should not be ignored, the
plugin must avoid duplicating resources from that entry.

### 4.2 Generated resource configuration

Generated resource configuration must be placed under the configured generated-resources directory
and added to the binary's configuration file directories so the compile task consumes it
automatically.

### 4.3 Reachability metadata collection

The `collectReachabilityMetadata` task must resolve metadata for the runtime classpath from the
configured metadata repository URI, version, exclusions, and module-to-config-version overrides.
Its output directory must be consumable by native compile tasks.

### 4.4 Missing metadata reports

The `listLibrariesMissingMetadata` task must inspect direct runtime dependencies, compare them to
the configured reachability metadata repository, write a JSON report, and optionally create GitHub
issues when the user supplies issue-creation settings.

### 4.5 Dynamic access metadata

When a binary emits a Native Image build report, the plugin must generate dynamic-access metadata
from that report and make it available as part of the binary's generated Native Image
configuration.

## 5. Native Image tracing agent

The Gradle plugin must make the Native Image tracing agent available without requiring users to
manually edit JVM task command lines.

### 5.1 Agent enablement

The agent can be enabled by DSL configuration or with the `-Pagent` Gradle property. When the
property names a mode, that mode must override the configured default mode for the instrumented
run.

### 5.2 Instrumented tasks

Every task that implements `JavaForkOptions` is eligible for instrumentation. The
`tasksToInstrumentPredicate` setting may narrow that set. Non-matching tasks must be skipped
without failing the build.

### 5.3 Agent modes

The Gradle DSL must expose standard, conditional, direct, and disabled agent modes using the
shared agent mode behavior from §FS-003-metadata-and-resource-workflows.3. Conditional mode must
support user-code and extra filters; direct mode must allow users to pass native agent options,
including `{output_dir}` substitution.

### 5.4 Agent output layout

Agent output must be written under `build/native/agent-output/<taskName>` unless users configure a
direct mode output location. Generated output must be suitable for later merge and copy steps.

### 5.5 Metadata copy

The `metadataCopy` task must copy or merge agent output from configured input tasks into configured
output directories. Command-line options on `metadataCopy` may select task names and destination
directories for ad hoc use.

## 6. Native tests

Gradle native tests are part of the same plugin workflow, but their runtime semantics are defined
by §FS-004-native-test-execution.

### 6.1 Test task integration

The default `test` binary must derive its classes, resources, classpath, test identifiers, and JUnit
support from the Gradle `test` source set and test task.

### 6.2 Native test execution

`nativeTestCompile` must build the native test image. `nativeTest` must run that image unless the
test support DSL disables native testing or the requested task graph only builds the test image.
Test result handling must integrate with Gradle task failures so native test failures fail the
build.

### 6.3 Compatibility mode

When Native Image compatibility mode is detected, Gradle native test behavior may use the original
JUnit ConsoleLauncher path rather than the Native Build Tools launcher path, as described by
§FS-004-native-test-execution.5.

## 7. Verification surface

The module's unit tests must cover task and plugin behavior that can be exercised without running
full sample builds. Functional tests must exercise sample projects through Gradle TestKit and the
repository's common test repository. Required scenario families include Java applications, Java
libraries, Kotlin tests, custom source sets, multi-project tests, resources, reflection, metadata
repository integration, agent collection, reachability metadata, Native Image options, and layered
applications. These fixtures are owned by §AR-004-samples-and-functional-fixtures.
