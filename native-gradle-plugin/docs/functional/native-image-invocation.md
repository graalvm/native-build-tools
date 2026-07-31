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

When no explicit launcher is set, the plugin selects a launcher by convention. The plugin
installs that convention on each native binary's public `javaLauncher` property
(`graalvmNative.binaries.all { javaLauncher.convention(…) }`), so the property resolves
to the convention value and stays queryable at configuration time:

1. **Toolchain detection** (`toolchainDetection = true`): `configureToolchain()` resolves
   the configured Java toolchain and exposes it as the binary's `javaLauncher` convention
   — it does **not** check for `native-image` presence. At build time,
   `NativeImageExecutableLocator` probes the toolchain's installation for
   `bin/native-image`. If found, that launcher is used. If `native-image` is not present,
   the locator falls through to environment variable fallback.
2. **Toolchain disabled**: the convention yields no launcher, and the plugin goes directly
   to environment variable fallback.

The convention-selected launcher is used only when it contains `bin/native-image`. If it
does not, the plugin continues to environment-variable fallback.

Because the convention is installed on the public property, the plugin tracks launcher
provenance independently of the property value: a launcher is *explicit* only when the
user assigned it directly
([§FS-native-invocation.1.1](native-image-invocation.md#11-explicit-java-launcher)); a value supplied by the
convention is never treated as explicit. Provenance must not be derived from property
presence (the property is present in both cases), nor from the resolved launcher (the
convention supplies the same launcher the default resolution would).

### 1.3. Environment-variable fallback

When no convention launcher supplies `native-image`, the plugin resolves a single primary
GraalVM home from the first available environment variable:

1. `GRAALVM_HOME` — if set
2. `JAVA_HOME` — if `GRAALVM_HOME` is not set
3. `{java.home}` — the Gradle JVM itself, as a last resort when neither env var is set

The plugin probes the primary home for `bin/native-image`. If `native-image` is not found
there, it attempts `gu install native-image` (see
[§FS-native-invocation.1.4](native-image-invocation.md#14-gu-based-installation)) on that same home.
If the primary GraalVM still lacks `native-image` after `gu`, the plugin probes the
*other* GraalVM homes — whichever of `GRAALVM_HOME`/`JAVA_HOME`/`{java.home}` was not
selected as the primary. If no `native-image` is found in any of those locations, the
locator fails with a diagnostic ([§FS-native-invocation.1.5](native-image-invocation.md#15-failure-messages)).

Because the resolved executable may come from any of `GRAALVM_HOME`, `JAVA_HOME`, or
`{java.home}`, compile and metadata tasks MUST model each of the three sources as a task
input whose value distinguishes unset, empty, and set states. A change to any source —
including one shadowed by an earlier fallback in the resolution order — MUST re-run the
task rather than leave it UP-TO-DATE with an executable resolved from the previous
environment. The fallback candidates probed at execution time MUST be derived from the
same providers that back those inputs, so the probe set and the up-to-date fingerprint
never diverge.

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
* for convention selection, whether toolchain detection was enabled and whether the
  launcher was convention-selected rather than explicitly configured
* which environment variables were (or were not) set
* whether the `gu` tool was available and whether installation was attempted
  ([§FS-native-invocation.1.4](native-image-invocation.md#14-gu-based-installation)) — "after attempting gu install" is
  reported only when `gu` actually ran, and a missing `gu` is reported as unavailable

The `NativeImageExecutableLocator.Diagnostics` class collects this information for the
`BuildNativeImageTask` to emit at build time.

### 1.6. Toolchain detection interaction

When `toolchainDetection = true` and the toolchain-resolved launcher does not contain
`native-image`, `configureToolchain()` in `DefaultGraalVmExtension` still returns the
toolchain launcher (it never checks for `native-image`). The
`NativeImageExecutableLocator` performs the `native-image` check at build time and falls
through to environment variable fallback (GRAALVM_HOME → JAVA_HOME → Gradle JVM) when
the toolchain's installation lacks `native-image`. This means the toolchain's GraalVM is
used only if it already provides `native-image`; otherwise the build uses the same
environment-variable resolution path as builds without a toolchain.

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
