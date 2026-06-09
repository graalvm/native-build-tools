# AR-gradle-plugin: The Gradle plugin adapts shared Native Image behavior to Gradle APIs

`native-gradle-plugin` owns Gradle plugin registration, extension objects, task types, task
actions, command-line providers, Gradle services, artifact transforms, and Gradle functional test
infrastructure. The module implements the focused Gradle functional specs under
`docs/functional/` by adapting the shared libraries from §common/AR-common-libraries into Gradle's
APIs, following §GOAL-idiomatic-gradle.

## 1. Module responsibility

The Gradle plugin module is the only place that should depend on Gradle plugin implementation APIs
for product behavior. It owns the `org.graalvm.buildtools.native` plugin class, public Gradle DSL
interfaces, task types, task option methods, command-line providers, and Gradle-specific logging
and diagnostics.

Public Gradle-facing classes are part of the plugin's compatibility surface. Shared common
libraries may expose Java APIs to the plugin, but they must not expose or depend on Gradle types.
Internal classes may use Gradle providers, services, artifact transforms, and configuration-cache
support. Behavior that can be expressed without Gradle APIs should be pushed down into common
modules only when Maven or shared tests also need it.

## 2. Extension and option model

The Gradle DSL is organized around a repository-level extension and per-binary option objects.
`DefaultGraalVmExtension` backs the public `GraalVMExtension` interface. It owns the binary
container, agent extension, generated-resource settings, reachability metadata settings, and
toolchain detection flags.

`NativeImageOptions` and its implementation hold compile and runtime options for a named binary.
The option object is shared by compile, run, resource-generation, dynamic-access, and native-test
tasks so one binary has one authoritative configuration model. Delegating option wrappers may be
used when a task needs a compile-only or runtime-only view of a binary, but the wrapper must not
fork behavior from the underlying `NativeImageOptions` object.

## 3. Task graph architecture

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
`common/junit-platform-native` as specified by §common/AR-common-libraries.4.

## 4. Command line and executable services

Gradle-specific process setup is separated from shared Native Image option semantics.
`NativeImageCommandLineProvider` converts `NativeImageOptions` into Native Image arguments. It may
use Gradle providers and file collections, but shared escaping and argument-file behavior must come
from common utilities.

`NativeImageExecutableLocator` encapsulates Gradle-specific discovery of `native-image`, including
Java launcher/toolchain integration and environment fallbacks. It also provides diagnostics for
failed lookup. `NativeImageService` and reachability metadata build services keep expensive or
shared state out of individual task instances and align with Gradle's configuration-cache and
service-use model.

## 5. Artifact transforms and classpath analysis

Gradle artifact transforms are used only for Gradle-specific performance and dependency graph
integration. The plugin registers a JAR analysis attribute and transform so classpath entries can
be inspected without making every task implement its own scan lifecycle.

Transform output must be treated as internal task input data. The functional behavior remains the
resource and classpath analysis contract in §common/FS-common-libraries.2.

## 6. Tests and fixtures

The module owns Gradle TestKit infrastructure, Gradle-specific fixtures, and Gradle sample
execution. Unit tests should cover task option mutation, command-line generation, classpath
analysis adapters, metadata tasks, and plugin registration behavior that can run without a full
Gradle sample build.

Functional tests should run real sample builds through TestKit. They must prefer scenario
fixtures over synthetic one-off projects when an existing sample already represents the behavior.
Shared samples and cross-plugin scenario ownership are described by §root/AR-build-infrastructure.4.1.

## 7. Dependency direction

`native-gradle-plugin` may depend on `common/utils`, `common/graalvm-reachability-metadata`, and
`common/junit-platform-native`. Those common modules must not depend on Gradle implementation
classes. Gradle-specific behavior should remain in this module unless it is actually
build-tool-neutral and can be expressed without Gradle APIs.
