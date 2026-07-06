# FS-native-invocation: Gradle tasks construct and execute Native Image invocations

Native Image invocation covers executable lookup, preflight checks, command-line assembly, and
process execution.

## 1. Executable discovery

Compile and metadata tasks must find `native-image` from the configured Gradle Java launcher or
toolchain when toolchain detection is enabled. When detection is disabled or no launcher supplies
Native Image, the plugin must use GraalVM/JDK environment and path fallbacks. Failure messages must
tell the user which lookup path was attempted.

## 2. Version and schema gates

When users configure a required Native Image version, compile tasks must check the discovered
version before building. When reachability metadata is enabled, tasks must validate repository
metadata against the schema expected by the discovered Native Image major version before passing
that metadata to `native-image`.

## 3. Command-line construction

The command line must combine classpath, module path where applicable, output name, main class,
boolean image flags, build arguments, JVM arguments, system properties, environment variables,
configuration directories, generated resources, reachability metadata, layer options
([§root/GLOSS-layered-image](../../../docs/spec/glossary.md#gloss-layered-image-layered-native-image)), and PGO options ([§root/GLOSS-pgo](../../../docs/spec/glossary.md#gloss-pgo-profile-guided-optimization-pgo)). Shared escaping and argument-file
conversion must come from common utilities rather than Gradle-only string handling, keeping Gradle
aligned with [§root/FS-option-precedence](../../../docs/spec/functional/option-precedence.md#fs-option-precedence-command-line-input-and-durable-configuration-produce-one-option-state).

For a layer created from declared JARs, the command line must use those JARs as its classpath so
the layer input remains limited to the declaration. A layer created from packages must instead
retain the binary classpath, which supplies the classes selected by those package names.

## 4. Argument files

The plugin must support Native Image argument files for command lines that should not be passed as
plain process arguments. Argument-file generation must preserve argument semantics and use paths
relative to the selected working directory where Native Image requires that form.

## 5. Classpath JAR and artifact analysis

When configured to use a classpath JAR, the compile task must pass the generated JAR instead of an
exploded classpath. The plugin may analyze runtime classpath JARs through Gradle artifact
transforms to discover packages and resource behavior, but that transform output is an internal
detail. The fat-jar form is defined in [§root/GLOSS-fat-jar](../../../docs/spec/glossary.md#gloss-fat-jar-fat-jar-classpath-jar).

## 6. Parallel native builds

The plugin must limit concurrent Native Image builds through a Gradle build service. Users can set
the limit with `org.graalvm.buildtools.max.parallel.builds` or
`GRAALVM_BUILDTOOLS_MAX_PARALLEL_BUILDS`; otherwise the plugin chooses a conservative default from
available processors.
