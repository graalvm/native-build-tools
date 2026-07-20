# FS-native-invocation: Gradle tasks construct and execute Native Image invocations

Native Image invocation covers executable lookup, preflight checks, command-line assembly, and
process execution.

## 1. Executable discovery

Compile and metadata tasks must find the `native-image` executable by probing, in order:

### 1.1. Explicit java launcher

When a user explicitly configures `javaLauncher` on a native binary
(`graalvmNative.binaries.all { javaLauncher.set(…) }`), that launcher is authoritative.
The plugin must probe its installation path for `bin/native-image`. If the executable is
missing, the build MUST fail with a diagnostic that names the launcher and its installation
path. The plugin MUST NOT silently fall back to another launcher,
environment variable, or path-based discovery — including `gu install native-image`.

### 1.2. Convention-selected launcher

When no explicit launcher is set, the plugin selects a launcher by convention:

1. **Toolchain detection** (`toolchainDetection = true`): the configured Java toolchain is
   probed for `bin/native-image`. If found, that launcher is used. If `native-image` is not
   present, `configureToolchain()` returns `null` and the plugin continues to environment
   variable fallback.
2. **Toolchain disabled**: no convention launcher is set, the plugin goes directly to
   environment variable fallback.

The convention-selected launcher is used only when it contains `bin/native-image`. If it
does not, the plugin continues to environment-variable fallback.

### 1.3. Environment-variable fallback

When no convention launcher supplies `native-image`, the plugin probes, in order:

1. `GRAALVM_HOME/bin/native-image`
2. `JAVA_HOME/bin/native-image`
3. `{java.home}/bin/native-image` — the Gradle JVM itself, as a last resort when neither
   environment variable is set

If none resolve to an existing executable, the plugin attempts `gu install native-image`
(see [§FS-native-invocation.1.4](native-image-invocation.md#14-gu-based-installation)) on the resolved
GraalVM home; if that still does not provide `native-image`, it probes the other GraalVM homes
(`GRAALVM_HOME`/`JAVA_HOME`/`{java.home}` other than the one just tried). If no `native-image` is
found in any of those locations, the locator fails with a diagnostic ([§FS-native-invocation.1.5](native-image-invocation.md#15-failure-messages)).

### 1.4. gu-based installation

When the GraalVM installation resolved through the environment-variable fallback (GRAALVM_HOME →
JAVA_HOME → Gradle JVM) contains a working `gu` tool but does not yet have `native-image`, the plugin MAY attempt
`gu install native-image`. This fallback MUST NOT apply when an explicit launcher
([§FS-native-invocation.1.1](native-image-invocation.md#11-explicit-java-launcher)) was configured — the
user-selected installation must provide `native-image` without implicit installation.

### 1.5. Failure messages

All failure messages MUST tell the user:

* which lookup paths were attempted
* for an explicit launcher, the launcher name and installation path
* for convention selection, whether toolchain detection was enabled
* which environment variables were (or were not) set

The `NativeImageExecutableLocator.Diagnostics` class collects this information for the
`BuildNativeImageTask` to emit at build time.

### 1.6. Toolchain detection interaction

When `toolchainDetection = true` and the toolchain-resolved launcher does not contain
`native-image`, `configureToolchain()` in `DefaultGraalVmExtension` returns `null`.
The plugin then falls through to environment variable fallback (GRAALVM_HOME →
JAVA_HOME → Gradle JVM). This means the toolchain's GraalVM is used only if it already
provides `native-image`; otherwise the build uses the same environment-variable resolution
path as builds without a toolchain.

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

When Gradle uses its plain console, the Native Image invocation must explicitly disable colorful
build output. Otherwise, the `richOutput` option controls Native Image's color-enabled argument,
adapting [§root/FS-native-builds.2](../../../docs/spec/functional/native-image-builds.md#2-command-line-construction).

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
