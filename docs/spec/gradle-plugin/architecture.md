# AR-001-gradle-plugin-boundary: The Gradle plugin module adapts shared behavior to Gradle APIs

`native-gradle-plugin` owns Gradle plugin registration, extension objects, task types, task actions,
command-line providers, Gradle services, artifact transforms, and Gradle functional test
infrastructure. The module implements §FS-001-gradle-plugin-native-image-workflow by adapting the
shared libraries from §AR-003-shared-common-libraries into Gradle's APIs.

## 1. Module responsibility

The Gradle plugin module is the only place that should depend on Gradle plugin implementation APIs
for product behavior.

### 1.1 Owned API surface

The module owns the `org.graalvm.buildtools.native` plugin class, public Gradle DSL interfaces,
task types, task option methods, command-line providers, and Gradle-specific logging and
diagnostics.

### 1.2 External boundary

Public Gradle-facing classes are part of the plugin's compatibility surface. Shared common
libraries may expose Java APIs to the plugin, but they must not expose or depend on Gradle types.

### 1.3 Internal boundary

Internal classes may use Gradle providers, services, artifact transforms, and configuration-cache
support. Behavior that can be expressed without Gradle APIs should be pushed down into common
modules only when Maven or shared tests also need it.

## 2. Extension and option model

The Gradle DSL is organized around a repository-level extension and per-binary option objects.

### 2.1 Extension implementation

`DefaultGraalVmExtension` backs the public `GraalVMExtension` interface. It owns the binary
container, agent extension, generated-resource settings, reachability metadata settings, and
toolchain detection flags.

### 2.2 Binary options

`NativeImageOptions` and its implementation hold compile and runtime options for a named binary.
The option object is shared by compile, run, resource-generation, dynamic-access, and native-test
tasks so one binary has one authoritative configuration model.

### 2.3 Delegating options

Delegating option wrappers may be used when a task needs a compile-only or runtime-only view of a
binary. The wrapper must not fork behavior from the underlying `NativeImageOptions` object.

## 3. Task graph architecture

The plugin builds a Gradle task graph from the binary container.

### 3.1 Automatic task creation

For each binary, the plugin registers a compile task, run task, resource config task, and dynamic
access metadata task where applicable. The `main` and `test` binaries receive stable conventional
task names; custom binaries receive derived names.

### 3.2 Compile task

`BuildNativeImageTask` owns Native Image process execution, declared inputs and outputs, output
file naming, version checks, schema validation hooks, and argument-file usage.

### 3.3 Run task

`NativeRunTask` owns execution of the compiled image and runtime arguments. It may add layer
library paths when a binary uses Native Image layers.

### 3.4 Metadata tasks

`GenerateResourcesConfigFile`, `GenerateDynamicAccessMetadata`, `CollectReachabilityMetadata`,
`ListLibrariesMissingMetadata`, and `MetadataCopyTask` adapt shared metadata behavior to Gradle
tasks and Gradle file properties.

### 3.5 Native test tasks

Native test task wiring is owned here, but launcher and JUnit registration behavior belongs to
`common/junit-platform-native` as specified by §AR-003-shared-common-libraries.3.

## 4. Command line and executable services

Gradle-specific process setup is separated from shared Native Image option semantics.

### 4.1 Command-line provider

`NativeImageCommandLineProvider` converts `NativeImageOptions` into Native Image arguments. It may
use Gradle providers and file collections, but shared escaping and argument-file behavior must come
from common utilities.

### 4.2 Executable locator

`NativeImageExecutableLocator` encapsulates Gradle-specific discovery of `native-image`, including
Java launcher/toolchain integration and environment fallbacks. It also provides diagnostics for
failed lookup.

### 4.3 Build service

`NativeImageService` and reachability metadata build services keep expensive or shared state out of
individual task instances and align with Gradle's configuration-cache and service-use model.

## 5. Artifact transforms and classpath analysis

Gradle artifact transforms are used only for Gradle-specific performance and dependency graph
integration.

### 5.1 JAR analysis attribute

The plugin registers a JAR analysis attribute and transform so classpath entries can be inspected
without making every task implement its own scan lifecycle.

### 5.2 Transform output

Transform output must be treated as internal task input data. The functional behavior remains the
resource and classpath analysis contract in §FS-003-metadata-and-resource-workflows.2.

## 6. Tests and fixtures

The module owns Gradle TestKit infrastructure, Gradle-specific fixtures, and Gradle sample
execution. Shared samples and cross-plugin scenario ownership are described by
§AR-004-samples-and-functional-fixtures.

### 6.1 Unit tests

Unit tests should cover task option mutation, command-line generation, classpath analysis adapters,
metadata tasks, and plugin registration behavior that can run without a full Gradle sample build.

### 6.2 Functional tests

Functional tests should run real sample builds through TestKit. They must prefer scenario fixtures
over synthetic one-off projects when an existing sample already represents the behavior.

## 7. Dependency direction

`native-gradle-plugin` may depend on `common/utils`, `common/graalvm-reachability-metadata`, and
`common/junit-platform-native`. Those common modules must not depend on Gradle implementation
classes. Gradle-specific behavior should remain in this module unless it is actually
build-tool-neutral and can be expressed without Gradle APIs.
