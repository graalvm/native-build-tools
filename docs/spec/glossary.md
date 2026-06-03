# GLOSS-compatibility-mode: Native Image compatibility mode

A Native Image build mode that trades some closed-world optimizations for broader runtime
compatibility. It changes native test execution because the test image may need JUnit's standard
`ConsoleLauncher` path instead of the Native Build Tools launcher, as specified by
§FS-native-tests.5.

# GLOSS-layered-image: Layered native image

A native image built in layers, where a base layer is reused by images that build on top of it.
Layer creation and consumption options feed Native Image command-line construction in
§gradle/FS-gradle-plugin.3.3, and run tasks may add layer library paths as in
§gradle/FS-gradle-plugin.2.2.

# GLOSS-dynamic-access-metadata: Dynamic access metadata

Reachability information about reflection, resources, JNI, and other dynamic access that Native
Image cannot infer statically. The plugins can generate it from a Native Image build report before
compilation, using the reachability metadata repository and the runtime classpath graph; see
§gradle/FS-gradle-plugin.4.5 and §maven/FS-maven-plugin.2.5.

# GLOSS-reachability-metadata-repository: GraalVM Reachability Metadata Repository

A versioned repository that maps dependency coordinates to Native Image configuration directories,
so projects can consume community-maintained metadata for third-party libraries instead of writing
their own. Query behavior is shared common code, specified by
§common/FS-common-libraries.5.

# GLOSS-tracing-agent: Native Image tracing agent

The `native-image-agent` JVM agent that records dynamic access while an application or test runs on
the JVM, producing metadata the plugins can merge and reuse for native builds. Its shared mode
model (standard, conditional, direct, disabled) is specified by
§common/FS-common-libraries.3.

# GLOSS-pgo: Profile-guided optimization (PGO)

A Native Image workflow that first builds an instrumented image, runs it to collect a profile, then
uses that profile to optimize a final image. The plugins expose PGO instrumentation as a build
option in §gradle/FS-gradle-plugin.2.4.

# GLOSS-fat-jar: Fat JAR (classpath JAR)

A single JAR that packages or references the full application classpath, passed to `native-image`
instead of an exploded classpath. The Gradle plugin can generate and consume it as described in
§gradle/FS-gradle-plugin.3.5.

# GLOSS-argument-file: Native Image argument file

A file passed to `native-image` as `@<path>` that carries arguments which should not be sent as
plain process arguments. Shared conversion and escaping utilities own its format, as specified by
§common/FS-common-libraries.1.
