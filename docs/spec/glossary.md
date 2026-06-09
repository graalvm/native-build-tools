# GLOSS-compatibility-mode: Native Image compatibility mode

A Native Image build mode that trades some closed-world optimizations for broader runtime
compatibility. It changes native test execution because the test image may need JUnit's standard
`ConsoleLauncher` path instead of the Native Build Tools launcher, as specified by
[§FS-native-tests.5](functional/native-tests.md#5-compatibility-mode).

# GLOSS-layered-image: Layered native image

A native image built in layers, where a base layer is reused by images that build on top of it.
Layer creation and consumption options feed Native Image command-line construction in
[§gradle/FS-native-invocation.3](../../native-gradle-plugin/docs/functional/native-image-invocation.md#3-command-line-construction), and run tasks may add layer library paths as in
[§gradle/FS-native-tasks.2](../../native-gradle-plugin/docs/functional/native-image-tasks.md#2-run-tasks).

# GLOSS-dynamic-access-metadata: Dynamic access metadata

Reachability information about reflection, resources, JNI, and other dynamic access that Native
Image cannot infer statically. The plugins can generate it from a Native Image build report before
compilation, using the reachability metadata repository and the runtime classpath graph; see
[§gradle/FS-resources-metadata.5](../../native-gradle-plugin/docs/functional/resources-and-metadata.md#5-dynamic-access-metadata) and [§maven/FS-native-builds.5](../../native-maven-plugin/docs/functional/native-image-builds.md#5-dynamic-access-metadata).

# GLOSS-reachability-metadata-repository: GraalVM Reachability Metadata Repository

A versioned repository that maps dependency coordinates to Native Image configuration directories,
so projects can consume community-maintained metadata for third-party libraries instead of writing
their own. Query behavior is shared common code, specified by
[§common/FS-common-libraries.5](../../common/docs/functional-spec.md#5-reachability-metadata-repository).

# GLOSS-tracing-agent: Native Image tracing agent

The `native-image-agent` JVM agent that records dynamic access while an application or test runs on
the JVM, producing metadata the plugins can merge and reuse for native builds. Its shared mode
model (standard, conditional, direct, disabled) is specified by
[§common/FS-common-libraries.3](../../common/docs/functional-spec.md#3-native-image-tracing-agent).

# GLOSS-pgo: Profile-guided optimization (PGO)

A Native Image workflow that first builds an instrumented image, runs it to collect a profile, then
uses that profile to optimize a final image. The plugins expose PGO instrumentation as a build
option in [§gradle/FS-native-tasks.4](../../native-gradle-plugin/docs/functional/native-image-tasks.md#4-command-line-overrides).

# GLOSS-fat-jar: Fat JAR (classpath JAR)

A single JAR that packages or references the full application classpath, passed to `native-image`
instead of an exploded classpath. The Gradle plugin can generate and consume it as described in
[§gradle/FS-native-invocation.5](../../native-gradle-plugin/docs/functional/native-image-invocation.md#5-classpath-jar-and-artifact-analysis).

# GLOSS-argument-file: Native Image argument file

A file passed to `native-image` as `@<path>` that carries arguments which should not be sent as
plain process arguments. Shared conversion and escaping utilities own its format, as specified by
[§common/FS-common-libraries.1](../../common/docs/functional-spec.md#1-shared-native-image-utilities).
