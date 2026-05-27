# GRADLE-plugin: The Gradle plugin wires Native Image behavior into Gradle builds

The `native-gradle-plugin` module provides the Gradle plugin identified as
`org.graalvm.buildtools.native`. Applying that plugin gives a Gradle project a GraalVM extension,
native-image related tasks, command-line providers, metadata tasks, and test integration that are
expressed using Gradle's plugin, task, provider, and configuration-cache conventions. This
functional contract realizes §GOAL-build-tool-native-image-workflows for Gradle and depends on
the shared behavior in §COMMON-libraries and §TESTING-native-tests-and-fixtures.

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
§TESTING-native-tests-and-fixtures. Run tasks must consume the compile task output file and pass runtime
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

### 2.5 Override precedence

Command-line task options and `-P` controls override DSL configuration for a single invocation
rather than merging with it. A `@Option` setter such as `--image-name` calls `set(...)` on the same
option property the DSL populates, so the command-line value replaces the DSL value for that build.
Build arguments are the documented exception: `--build-args` appends to the configured arguments
while `--force-build-args` replaces them. The `-Pagent` property overrides the configured agent
default mode as in §GRADLE-plugin.5.1. Because every source funnels
into one option object, behavior depends only on which source last wrote a value, not on where it
was written. This mirrors the Maven precedence rule in
§MAVEN-plugin.3.5.

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
options (§GLOSS-layered-image), and PGO options (§GLOSS-pgo). Shared command-line escaping and argument-file conversion must come from
common utilities rather than Gradle-only string handling. This keeps Gradle aligned with the Maven
contract in §MAVEN-plugin.6.2.

### 3.4 Argument files

The plugin must support Native Image argument files for command lines that should not be passed as
plain process arguments. Argument-file generation must preserve argument semantics and use paths
relative to the selected working directory where that is required by Native Image.

### 3.5 Classpath JAR and artifact analysis

When configured to use a classpath JAR, the compile task must receive the generated JAR rather than
an exploded classpath. The plugin may analyze runtime classpath JARs through Gradle artifact
transforms to discover packages and resource behavior, but the transform output must remain an
internal implementation detail. The fat-jar form is defined in §GLOSS-fat-jar.

### 3.6 Parallel native builds

The plugin must limit concurrent Native Image builds through a Gradle build service. The limit
must be configurable through the `org.graalvm.buildtools.max.parallel.builds` Gradle property or
the `GRAALVM_BUILDTOOLS_MAX_PARALLEL_BUILDS` environment variable, with a conservative default
based on available processors.

## 4. Resources and reachability metadata

Gradle projects use the shared metadata and resource contracts in
§COMMON-libraries through Gradle tasks and extension settings.

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

When a binary is configured to emit a Native Image build report, the plugin must generate
dynamic-access metadata before invoking Native Image, using the configured reachability metadata
repository and runtime classpath graph, and make it available as part of the binary's generated
Native Image configuration. The metadata is defined in §GLOSS-dynamic-access-metadata.

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
shared agent mode behavior from §COMMON-libraries.3. Conditional mode must
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
by §TESTING-native-tests-and-fixtures.

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
§TESTING-native-tests-and-fixtures.5.

## 7. Verification surface

The module's unit tests must cover task and plugin behavior that can be exercised without running
full sample builds. Functional tests must exercise sample projects through Gradle TestKit and the
repository's common test repository. Required scenario families include Java applications, Java
libraries, Kotlin tests, custom source sets, multi-project tests, resources, reflection, metadata
repository integration, agent collection, reachability metadata, Native Image options, and layered
applications. These fixtures are owned by §TESTING-native-tests-and-fixtures.7.

## 8. Architecture

`native-gradle-plugin` owns Gradle plugin registration, extension objects, task types, task
actions, command-line providers, Gradle services, artifact transforms, and Gradle functional test
infrastructure. The module implements §GRADLE-plugin by adapting the
shared libraries from §COMMON-libraries.9 into Gradle's APIs.

### 8.1 Module responsibility

The Gradle plugin module is the only place that should depend on Gradle plugin implementation APIs
for product behavior. It owns the `org.graalvm.buildtools.native` plugin class, public Gradle DSL
interfaces, task types, task option methods, command-line providers, and Gradle-specific logging
and diagnostics.

Public Gradle-facing classes are part of the plugin's compatibility surface. Shared common
libraries may expose Java APIs to the plugin, but they must not expose or depend on Gradle types.
Internal classes may use Gradle providers, services, artifact transforms, and configuration-cache
support. Behavior that can be expressed without Gradle APIs should be pushed down into common
modules only when Maven or shared tests also need it.

### 8.2 Extension and option model

The Gradle DSL is organized around a repository-level extension and per-binary option objects.
`DefaultGraalVmExtension` backs the public `GraalVMExtension` interface. It owns the binary
container, agent extension, generated-resource settings, reachability metadata settings, and
toolchain detection flags.

`NativeImageOptions` and its implementation hold compile and runtime options for a named binary.
The option object is shared by compile, run, resource-generation, dynamic-access, and native-test
tasks so one binary has one authoritative configuration model. Delegating option wrappers may be
used when a task needs a compile-only or runtime-only view of a binary, but the wrapper must not
fork behavior from the underlying `NativeImageOptions` object.

### 8.3 Task graph architecture

For each binary, the plugin registers a compile task, run task, resource config task, and dynamic
access metadata task where applicable. The `main` and `test` binaries receive stable conventional
task names; custom binaries receive derived names.

`BuildNativeImageTask` owns Native Image process execution, declared inputs and outputs, output
file naming, version checks, schema validation hooks, and argument-file usage. `NativeRunTask`
owns execution of the compiled image and runtime arguments, including layer library paths when a
binary uses Native Image layers. `GenerateResourcesConfigFile`, `GenerateDynamicAccessMetadata`,
`CollectReachabilityMetadata`, `ListLibrariesMissingMetadata`, and `MetadataCopyTask` adapt shared
metadata behavior to Gradle tasks and Gradle file properties.

Native test task wiring is owned here, but launcher and JUnit registration behavior belongs to
`common/junit-platform-native` as specified by §COMMON-libraries.9.4.

### 8.4 Command line and executable services

Gradle-specific process setup is separated from shared Native Image option semantics.
`NativeImageCommandLineProvider` converts `NativeImageOptions` into Native Image arguments. It may
use Gradle providers and file collections, but shared escaping and argument-file behavior must come
from common utilities.

`NativeImageExecutableLocator` encapsulates Gradle-specific discovery of `native-image`, including
Java launcher/toolchain integration and environment fallbacks. It also provides diagnostics for
failed lookup. `NativeImageService` and reachability metadata build services keep expensive or
shared state out of individual task instances and align with Gradle's configuration-cache and
service-use model.

### 8.5 Artifact transforms and classpath analysis

Gradle artifact transforms are used only for Gradle-specific performance and dependency graph
integration. The plugin registers a JAR analysis attribute and transform so classpath entries can
be inspected without making every task implement its own scan lifecycle.

Transform output must be treated as internal task input data. The functional behavior remains the
resource and classpath analysis contract in §COMMON-libraries.2.

### 8.6 Tests and fixtures

The module owns Gradle TestKit infrastructure, Gradle-specific fixtures, and Gradle sample
execution. Unit tests should cover task option mutation, command-line generation, classpath
analysis adapters, metadata tasks, and plugin registration behavior that can run without a full
Gradle sample build.

Functional tests should run real sample builds through TestKit. They must prefer scenario
fixtures over synthetic one-off projects when an existing sample already represents the behavior.
Shared samples and cross-plugin scenario ownership are described by
§TESTING-native-tests-and-fixtures.7.

### 8.7 Dependency direction

`native-gradle-plugin` may depend on `common/utils`, `common/graalvm-reachability-metadata`, and
`common/junit-platform-native`. Those common modules must not depend on Gradle implementation
classes. Gradle-specific behavior should remain in this module unless it is actually
build-tool-neutral and can be expressed without Gradle APIs.
