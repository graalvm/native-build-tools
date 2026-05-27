# GLOSS-001-compatibility-mode: Native Image compatibility mode

A Native Image build mode that trades some closed-world optimizations for broader runtime
compatibility. It changes native test execution because the test image may need JUnit's standard
`ConsoleLauncher` path instead of the Native Build Tools launcher, as specified by
§FS-004-native-test-execution.5.

# GLOSS-002-layered-image: Layered native image

A native image built in layers, where a base layer is reused by images that build on top of it.
Layer creation and consumption options feed Native Image command-line construction in
§FS-001-gradle-plugin-native-image-workflow.3.3, and run tasks may add layer library paths as in
§AR-001-gradle-plugin-boundary.3.3.

# GLOSS-003-dynamic-access-metadata: Dynamic access metadata

Reachability information about reflection, resources, JNI, and other dynamic access that Native
Image cannot infer statically. The plugins can generate it from a Native Image build report before
compilation, using the reachability metadata repository and the runtime classpath graph; see
§FS-001-gradle-plugin-native-image-workflow.4.5 and §FS-002-maven-plugin-native-image-workflow.2.5.

# GLOSS-004-reachability-metadata-repository: GraalVM Reachability Metadata Repository

A versioned repository that maps dependency coordinates to Native Image configuration directories,
so projects can consume community-maintained metadata for third-party libraries instead of writing
their own. Query behavior is shared common code, specified by
§FS-003-metadata-and-resource-workflows.5.

# GLOSS-005-tracing-agent: Native Image tracing agent

The `native-image-agent` JVM agent that records dynamic access while an application or test runs on
the JVM, producing metadata the plugins can merge and reuse for native builds. Its shared mode
model (standard, conditional, direct, disabled) is specified by
§FS-003-metadata-and-resource-workflows.3.

# GLOSS-006-pgo: Profile-guided optimization (PGO)

A Native Image workflow that first builds an instrumented image, runs it to collect a profile, then
uses that profile to optimize a final image. The plugins expose PGO instrumentation as a build
option in §FS-001-gradle-plugin-native-image-workflow.2.4.

# GLOSS-007-fat-jar: Fat JAR (classpath JAR)

A single JAR that packages or references the full application classpath, passed to `native-image`
instead of an exploded classpath. The Gradle plugin can generate and consume it as described in
§FS-001-gradle-plugin-native-image-workflow.3.5.

# GLOSS-008-argument-file: Native Image argument file

A file passed to `native-image` as `@<path>` that carries arguments which should not be sent as
plain process arguments. Shared conversion and escaping utilities own its format, as specified by
§FS-003-metadata-and-resource-workflows.1.2.
