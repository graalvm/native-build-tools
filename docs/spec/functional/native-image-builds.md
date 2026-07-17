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
