# AR-common-libraries: Shared libraries stay independent from Gradle and Maven APIs

The `common/` directory contains libraries that should remain independent from Gradle and Maven
plugin APIs unless a dependency is explicitly part of that library's role. These modules implement
§FS-common-libraries and the shared test runtime in §root/FS-native-tests under
§GRUND-common-purpose.

## 1. Common module set

The common directory is split by product-neutral responsibility rather than by build-tool
consumer. `common/utils` owns shared agent configuration models, resource configuration models,
classpath and JAR analysis, Native Image command-line utilities, schema validation helpers, JUnit
dependency helpers, and shared constants.

`common/graalvm-reachability-metadata` owns metadata repository access, artifact and module
indexes, directory configuration, query objects, version selection, and missing metadata command
support. `common/junit-platform-native` owns JUnit Platform support that is needed inside native
test images, including native-image feature registration, test class registration, launcher
behavior, and JUnit configuration providers.

## 2. Utility architecture

Utilities must remain Java libraries with plain inputs and outputs. Native Image utilities accept
strings, paths, versions, and option collections; they must not know whether a caller is Gradle,
Maven, a unit test, or another tool.

Resource analyzers operate on classpath directories and JAR files. Build-tool integrations are
responsible for selecting classpath entries; analyzers are responsible for interpreting those
entries. Agent mode objects expose command-line fragments for `native-image-agent` and
`native-image-configure`; build-tool integrations own how those fragments are attached to JVM
processes or helper invocations.

Schema validation utilities own the mapping from metadata file type and Native Image major
version to schema validation behavior. Product plugins supply the metadata root and version.

## 3. Reachability metadata architecture

The reachability metadata module is the repository query engine for product plugins. It exposes
repository interfaces that answer coordinate/version questions without exposing the storage
implementation to Gradle or Maven code.

The file-system implementation parses repository JSON indexes from an unpacked metadata
repository. It owns path normalization and directory lookup rules for metadata entries. Artifact
and module indexes are represented as structured model objects; parsing code must keep repository
schema details inside the module so product plugins deal in query results.

Missing metadata support formats reports and optional issue creation data from repository query
results. Product plugins provide credentials, project names, and target repository settings.

## 4. JUnit native architecture

JUnit native support is a runtime library used inside native test images and a compile-time helper
for Native Image features. `NativeImageJUnitLauncher` is the runtime entry point for native test
images. It loads selected test identifiers, builds JUnit Platform launcher requests, executes
tests, and reports process outcomes.

`JUnitPlatformFeature` runs during Native Image analysis and registers classes, resources, and
configuration needed by the launcher and supported test engines. Config providers are
service-loaded contributors for platform, Jupiter, Vintage, and related JUnit behavior.

The module may ship Native Image resource files such as initialization directives or excluded
configuration. Product plugins consume those files through classpath inclusion, not by duplicating
their contents.

## 5. Dependency direction

Product plugins may depend on common modules. Common modules should not depend on product plugin
implementation classes. This keeps §root/GOAL-plugin-parity
achievable without making Gradle and Maven integration code mutually dependent.
§REQ-no-buildtool-apis.

Common modules may depend on Java libraries, JSON/schema tooling, JUnit Platform APIs where the
module's responsibility requires them, and Native Image APIs where the module is explicitly a
Native Image runtime or feature integration. They must not depend on Gradle API classes, Maven
plugin API classes, build-tool test fixtures, or product plugin implementation classes.

Behavior should be promoted from a product plugin into common code only when it is genuinely
build-tool-neutral and at least one of these is true: both product plugins need it, shared tests
need it, or keeping it product-specific would create inconsistent Native Image semantics.
§GOAL-shared-native-image.
