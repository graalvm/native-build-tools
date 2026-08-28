# FS-native-builds: Both plugins build native images from build-tool project state

Both product plugins must translate Gradle or Maven project state into a single `native-image`
invocation. The user's durable configuration lives in the build file (Gradle DSL or Maven XML);
one-off command-line overrides feed the same command-line assembly path so behavior does not
diverge by configuration source. This contract realizes [§GOAL-plugin-parity](../goals.md#goal-plugin-parity-shared-native-image-behavior-remains-consistent-across-gradle-and-maven)
together with [§FS-plugin-common](plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior), and is adapted by [§gradle/FS-native-tasks](../../../native-gradle-plugin/docs/functional/native-image-tasks.md#fs-native-tasks-gradle-native-image-tasks-build-and-run-native-image-outputs) and
[§gradle/FS-native-invocation](../../../native-gradle-plugin/docs/functional/native-image-invocation.md#fs-native-invocation-gradle-tasks-construct-and-execute-native-image-invocations) for Gradle and [§maven/FS-goal-surface.1](../../../native-maven-plugin/docs/functional/goal-surface.md#1-build-goals) and
[§maven/FS-native-builds](../../../native-maven-plugin/docs/functional/native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state) for Maven.

## 1. Required inputs

A native image build must derive the following from project state and configuration:

| Input | Contract |
| --- | --- |
| Classpath and module path | Derived from selected build-tool project state. |
| Entry point or shared-library mode | Derived from user configuration, build-tool conventions, or build-tool-specific discovery. |
| Image name and output location | Derived from user configuration or stable build-tool conventions. |
| Build arguments | Combined from durable configuration and documented command-line overrides. |
| JVM arguments, system properties, environment variables | Passed to the `native-image` driver process when configured. |
| Configuration file directories | Include generated resource config ([§FS-resources-and-metadata.1](resources-and-metadata.md#1-resource-configuration)), resolved repository metadata ([§FS-resources-and-metadata.2](resources-and-metadata.md#2-reachability-metadata-repository)), and dynamic access metadata ([§FS-resources-and-metadata.4](resources-and-metadata.md#4-dynamic-access-metadata)). |
| Optional inputs | Include classpath JAR ([§GLOSS-fat-jar](../glossary.md#gloss-fat-jar-fat-jar-classpath-jar)), argument file ([§GLOSS-argument-file](../glossary.md#gloss-argument-file-native-image-argument-file)), layer options ([§GLOSS-layered-image](../glossary.md#gloss-layered-image-layered-native-image)), and PGO options ([§GLOSS-pgo](../glossary.md#gloss-pgo-profile-guided-optimization-pgo)) when supported by the plugin. |

Gradle-specific task inputs are specified by [§gradle/FS-native-tasks](../../../native-gradle-plugin/docs/functional/native-image-tasks.md#fs-native-tasks-gradle-native-image-tasks-build-and-run-native-image-outputs) and
[§gradle/FS-native-invocation](../../../native-gradle-plugin/docs/functional/native-image-invocation.md#fs-native-invocation-gradle-tasks-construct-and-execute-native-image-invocations). Maven-specific goal inputs are specified by
[§maven/FS-goal-surface](../../../native-maven-plugin/docs/functional/goal-surface.md#fs-goal-surface-maven-goals-expose-native-image-workflows), [§maven/FS-native-builds](../../../native-maven-plugin/docs/functional/native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state), and
[§maven/FS-config-model](../../../native-maven-plugin/docs/functional/configuration-model.md#fs-config-model-maven-xml-and-command-line-properties-configure-native-image-builds).

## 2. Command-line construction

Both plugins must construct the `native-image` command line through shared utilities from
[§common/FS-common-libraries.1](../../../common/docs/functional-spec.md#1-shared-native-image-utilities) so escaping, quoting, and argument-file conversion stay identical.
Plugin-specific string handling must not bypass those utilities.

When a build tool disables colored console output, its adapter must explicitly disable Native
Image colors with the flag supported by the discovered Native Image version. When console colors
are enabled, plugin-specific rich-output configuration may enable them explicitly. User-supplied
build arguments retain precedence over the adapter's detected console mode.

When a user-configured option set exceeds platform argument limits, or when configuration requests
it explicitly, the command line must be written as a Native Image argument file (`@<path>`).

## 3. Executable lookup

`native-image` must be located from the configured Java toolchain when toolchain detection is
enabled, then from `GRAALVM_HOME`/`JAVA_HOME`/`PATH` fallbacks. Failure messages must name which
lookup paths were attempted and what was found.

## 4. Version and schema gates

When the user configures a required Native Image version, the build must fail before invoking
`native-image` if the discovered version is older. When repository metadata is consumed, the build
must validate that metadata against the schema expected by the discovered Native Image major
version before passing it to `native-image` ([§FS-resources-and-metadata.5](resources-and-metadata.md#5-schema-validation)). These gates surface declared compatibility boundaries; they
must not mask Native Image constraints or bugs that belong upstream in GraalVM
([§NGOAL-graalvm-is-graalvm](../non-goals.md#ngoal-graalvm-is-graalvm-graalvm-constraints-and-bugs-are-not-a-matter-of-build-tools)).

## 5. Shared library mode

Both plugins must support shared-library output where the build tool's packaging model allows it.
Shared-library mode disables entry-point requirements and may change the output file extension.
The plugin-specific configuration surface and defaults are specified by
[§gradle/FS-plugin-model](../../../native-gradle-plugin/docs/functional/plugin-model.md#fs-plugin-model-gradle-plugin-activation-and-dsl-model) and [§maven/FS-config-model](../../../native-maven-plugin/docs/functional/configuration-model.md#fs-config-model-maven-xml-and-command-line-properties-configure-native-image-builds).

## 6. Layered images

Both plugins must support named layer creation from resolved build-tool dependencies and layer
consumption through declared task or artifact relationships. Selection rendering belongs to
common utilities; Gradle owns provider/task wiring and Maven owns goal/artifact/repository wiring.
The two surfaces may use build-tool-native syntax but must preserve equivalent selector and
multi-layer behavior. The `all` selector includes the complete runtime dependency graph, including
artifacts produced by other projects in the same multi-project or reactor build. Empty layer
selections fail before Native Image is invoked. Layer consumers retain the classpath or modulepath
inputs used to create their producer layers; Gradle propagates those build-local inputs through
its task graph, while Maven consumers declare equivalent dependencies. Layered executable
deployment must make each layer's runtime native files discoverable by the platform loader:
`LD_LIBRARY_PATH` on Linux, `DYLD_LIBRARY_PATH` on macOS, or `PATH` on Windows.

The `.nil` file is a build-time input consumed through `-H:LayerUse`; it is not the layer's runtime
payload. Native Image also produces platform-specific runtime libraries beside the `.nil`, and the
final image loads those files when it runs. Build-tool run and native-test tasks must configure the
loader for execution from the build tree. Repository and distribution flows must carry or stage
the runtime files in a consumer-owned location and must not depend on a producer build directory.
Publishing or attaching only a `.nil` is therefore not a complete deployable repository flow.

Layer consumption is supported on GraalVM 25.1 and later. GraalVM 25.0.x consumption remains
permitted but unsupported and must warn that it proceeds at the user's own risk; layer creation
alone does not warn. Native Image layers remain experimental upstream
([§DEC-layer-model.4](../decisions/layer-model.md#4-graalvm-release-support)).
[§GOAL-plugin-parity](../goals.md#goal-plugin-parity-shared-native-image-behavior-remains-consistent-across-gradle-and-maven).

## 7. Dependency preservation

Both plugins must let their approved native-image scopes preserve selected Gradle or Maven
dependencies by resolving build-tool dependency coordinates, optionally with their transitive
closure, to concrete classpath paths and rendering one `-H:Preserve=path=...` argument through
common utilities. The selection is opt-in, preserves transitive dependencies by default, and must
fail before Native Image starts when coordinates are blank, unresolved, ambiguous, empty, or have
no usable output. The generated Preserve argument precedes user build arguments so explicit
pass-through arguments retain their normal precedence.

The first-class build-tool surface is limited to dependency selection because coordinate-to-path
resolution adds behavior unavailable through static arguments. Users must continue to pass
`all`, `module=`, `package=`, and explicit `path=` Preserve selectors through normal build arguments,
protecting [§NGOAL-no-flag-mirroring](../non-goals.md#ngoal-no-flag-mirroring-the-plugins-do-not-add-build-tool-flags-that-only-forward-to-native-image-flags).
Gradle exposes the selection on every binary option object; Maven exposes it on the
`compile-no-fork` hierarchy (`compile`, `compile-no-fork`, the deprecated `build` alias, and
`write-args-file`), not native-test or layer-create goals. Preserve is available with GraalVM 25 and later and does not require the
experimental-option unlock sequence. Specific dependency selectors are preferred because
preservation can increase analysis work and image size.
[§GOAL-plugin-parity](../goals.md#goal-plugin-parity-shared-native-image-behavior-remains-consistent-across-gradle-and-maven),
[§REQ-backwards-compatibility.2](../requirements.md#2-configuration-compatibility).
