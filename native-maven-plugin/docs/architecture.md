# AR-maven-plugin: The Maven plugin adapts shared Native Image behavior to Maven APIs

`native-maven-plugin` owns Maven mojos, Maven plugin descriptor generation, Plexus integration,
Maven configuration objects, Maven repository seeding for functional tests, Maven-specific SBOM
integration, and issue reproducers. The module implements §FS-maven-plugin by adapting the shared
libraries from §common/AR-common-libraries into Maven's APIs.

## 1. Module responsibility

The Maven plugin module is the only place that should depend on Maven plugin implementation APIs
for product behavior. It owns all `@Mojo` classes, Maven parameter names, generated plugin
descriptors, Maven-specific configuration objects, Maven lifecycle bindings, and Maven
logging/exception translation.

Mojo names, parameter names, default phases, and documented system properties are Maven-facing
compatibility surfaces. Shared common libraries may expose Java APIs to the plugin, but they must
not expose or depend on Maven types. Maven-specific concerns such as project packaging, plugin
configuration lookup, toolchain manager integration, repository resolution, and plugin-manager
execution stay inside this module.

## 2. Mojo hierarchy

The mojo hierarchy separates general Maven setup from individual workflow goals.
`AbstractNativeMojo` owns shared Maven state and configuration that is common to native goals.
`AbstractNativeImageMojo` owns native-image command construction, executable lookup integration,
dependency classpath assembly, environment setup, schema validation hooks, and process execution.

`NativeCompileNoForkMojo` contains lifecycle-safe native image build behavior. `NativeCompileMojo`
forks the Maven lifecycle to package before building. Deprecated build aliases should subclass or
delegate to the current compile implementation. `NativeTestMojo` specializes the native image base
for test classpaths, JUnit dependencies, launcher selection, test identifier handling, and native
test execution.

Resource generation, dynamic access metadata, reachability metadata addition, merge-agent-files,
metadata-copy, missing metadata listing, and write-args-file each get dedicated mojos. They should
reuse common utility classes rather than duplicating Native Image metadata logic.

## 3. Configuration and Maven integration

Parameters must be declared on mojos or shared base classes with Maven's `@Parameter` annotation
so Maven descriptors, documentation, command-line properties, and injection stay aligned.

Main class discovery and similar behavior may inspect other Maven plugins in the project model,
but this lookup must remain defensive: missing plugins, missing nodes, and expression-evaluation
failures should not fail the build when explicit Native Build Tools configuration can still
succeed.

When one Native Build Tools goal needs another goal's output, it may invoke the goal through
Maven's plugin-manager execution path. Dependency resolution, artifact lookup, remote repositories,
and Maven toolchains must be accessed through Maven/Aether APIs inside this module. Common modules
should receive resolved paths, coordinates, or repository data rather than Maven session objects.

## 4. Native Image invocation architecture

Classpath assembly starts from Maven dependency scopes and project output directories, then applies
explicit classpath overrides and artifact exclusions. Test execution extends that classpath with
test outputs, test resources, JUnit dependencies, and Native Build Tools test support.

Command-line assembly must use shared Native Image utilities for escaping, argument files, version
parsing, and constants. Maven-specific code should decide which options are present; common code
should decide how those options are serialized.

`NativeImageConfigurationUtils` owns Maven-side executable lookup through toolchains,
`GRAALVM_HOME`, `JAVA_HOME`, and `PATH`. Lookup errors must be translated into Maven execution
errors with actionable diagnostics. SBOM generation is Maven-specific because it invokes Maven
plugin goals and uses Maven project state, and it should remain isolated from general native-image
command construction.

## 5. Agent and metadata architecture

Maven-specific agent configuration classes map XML elements to common agent mode objects. They
should keep XML compatibility while preserving shared mode semantics from §common/FS-common-libraries.3.

Agent merge and metadata copy behavior should share an abstract merge base where they need the
same tool discovery, stage selection, and `native-image-configure` invocation logic. Reachability
metadata mojos may use Maven dependency data to identify project coordinates, but repository query
and version selection belong to `common/graalvm-reachability-metadata`.

## 6. Functional test infrastructure

The module owns Maven-specific functional test infrastructure and reproducers. Functional tests
seed a local Maven repository so sample projects can resolve the plugin and support artifacts
without depending on external publication.

Samples under `samples/` provide cross-plugin Maven scenarios when possible. Dedicated
`native-maven-plugin/reproducers/` projects are appropriate for Maven-specific regressions whose
shape would not make sense as a shared sample.

## 7. Dependency direction

`native-maven-plugin` may depend on `common/utils`, `common/graalvm-reachability-metadata`, and
`common/junit-platform-native`. Those common modules must not depend on Maven implementation
classes. Maven-specific lifecycle, repository, descriptor, and configuration concerns should
remain in this module unless the behavior can be expressed as build-tool-neutral Native Image
support.
