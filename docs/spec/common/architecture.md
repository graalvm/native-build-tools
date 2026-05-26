# AR-003-shared-common-libraries: Common modules own build-tool-neutral Native Image support

The `common/` directory contains libraries that should remain independent from Gradle and Maven
plugin APIs unless a dependency is explicitly part of that library's role. These modules implement
§FS-003-metadata-and-resource-workflows and the shared test runtime in
§FS-004-native-test-execution.

## 1. Common module set

The common directory is split by product-neutral responsibility rather than by build-tool
consumer.

### 1.1 `common/utils`

`common/utils` owns shared agent configuration models, resource configuration models, classpath
and JAR analysis, Native Image command-line utilities, schema validation helpers, JUnit dependency
helpers, and shared constants.

### 1.2 `common/graalvm-reachability-metadata`

`common/graalvm-reachability-metadata` owns metadata repository access, artifact and module
indexes, directory configuration, query objects, version selection, and missing metadata command
support.

### 1.3 `common/junit-platform-native`

`common/junit-platform-native` owns JUnit Platform support that is needed inside native test
images, including native-image feature registration, test class registration, launcher behavior,
and JUnit configuration providers.

## 2. Utility architecture

Utilities must remain Java libraries with plain inputs and outputs.

### 2.1 Native Image utilities

Native Image utilities accept strings, paths, versions, and option collections. They must not know
whether a caller is Gradle, Maven, a unit test, or another tool.

### 2.2 Resource model

Resource analyzers operate on classpath directories and JAR files. Build-tool integrations are
responsible for selecting classpath entries; analyzers are responsible for interpreting those
entries.

### 2.3 Agent model

Agent mode objects expose command-line fragments for `native-image-agent` and
`native-image-configure`. Build-tool integrations own how those fragments are attached to JVM
processes or helper invocations.

### 2.4 Schema validation

Schema validation utilities own the mapping from metadata file type and Native Image major
version to schema validation behavior. Product plugins supply the metadata root and version.

## 3. Reachability metadata architecture

The reachability metadata module is the repository query engine for product plugins.

### 3.1 Repository abstraction

The module exposes repository interfaces that answer coordinate/version questions without exposing
the storage implementation to Gradle or Maven code.

### 3.2 File-system repository

The file-system implementation parses repository JSON indexes from an unpacked metadata
repository. It owns path normalization and directory lookup rules for metadata entries.

### 3.3 Index model

Artifact and module indexes are represented as structured model objects. Parsing code must keep
repository schema details inside the module so product plugins deal in query results.

### 3.4 Missing metadata support

Missing metadata support formats reports and optional issue creation data from repository query
results. Product plugins provide credentials, project names, and target repository settings.

## 4. JUnit native architecture

JUnit native support is a runtime library used inside native test images and a compile-time helper
for Native Image features.

### 4.1 Launcher

`NativeImageJUnitLauncher` is the runtime entry point for native test images. It loads selected
test identifiers, builds JUnit Platform launcher requests, executes tests, and reports process
outcomes.

### 4.2 Native Image feature

`JUnitPlatformFeature` runs during Native Image analysis and registers classes, resources, and
configuration needed by the launcher and supported test engines.

### 4.3 Config providers

Config providers are service-loaded contributors for platform, Jupiter, Vintage, and related
JUnit behavior. Providers should be added or updated when supported test behavior expands.

### 4.4 Resource files

The module may ship Native Image resource files such as initialization directives or excluded
configuration. Product plugins consume those files through classpath inclusion, not by duplicating
their contents.

## 5. Dependency direction

Product plugins may depend on common modules. Common modules should not depend on product plugin
implementation classes. This keeps §GOAL-002-shared-native-image-behavior-stays-consistent
achievable without making Gradle and Maven integration code mutually dependent.

### 5.1 Allowed dependencies

Common modules may depend on Java libraries, JSON/schema tooling, JUnit Platform APIs where the
module's responsibility requires them, and Native Image APIs where the module is explicitly a
Native Image runtime or feature integration.

### 5.2 Disallowed dependencies

Common modules must not depend on Gradle API classes, Maven plugin API classes, build-tool test
fixtures, or product plugin implementation classes.

### 5.3 Promotion rule

Behavior should be promoted from a product plugin into common code only when it is genuinely
build-tool-neutral and at least one of these is true: both product plugins need it, shared tests
need it, or keeping it product-specific would create inconsistent Native Image semantics.
