# FS-native-image-builds: Both plugins build native images from build-tool project state

Both product plugins must translate Gradle or Maven project state into a single `native-image`
invocation. The user's durable configuration lives in the build file (Gradle DSL or Maven XML);
one-off command-line overrides feed the same command-line assembly path so behavior does not
diverge by configuration source. This contract realizes §GOAL-plugin-parity
together with §FS-plugin-common-behavior, and is adapted by §gradle/FS-gradle-native-image-tasks and
§gradle/FS-gradle-native-image-invocation for Gradle and §maven/FS-maven-goal-surface.1 and
§maven/FS-maven-native-image-builds for Maven.

## 1. Required inputs

A native image build must derive the following from project state and configuration:

| Input | Contract |
| --- | --- |
| Classpath and module path | Derived from selected build-tool project state. |
| Entry point or shared-library mode | Derived from user configuration, build-tool conventions, or build-tool-specific discovery. |
| Image name and output location | Derived from user configuration or stable build-tool conventions. |
| Build arguments | Combined from durable configuration and documented command-line overrides. |
| JVM arguments, system properties, environment variables | Passed to the `native-image` driver process when configured. |
| Configuration file directories | Include generated resource config (§FS-resources-and-metadata.1), resolved repository metadata (§FS-resources-and-metadata.2), and dynamic access metadata (§FS-resources-and-metadata.4). |
| Optional inputs | Include classpath JAR (§GLOSS-fat-jar), argument file (§GLOSS-argument-file), layer options (§GLOSS-layered-image), and PGO options (§GLOSS-pgo) when supported by the plugin. |

Gradle-specific task inputs are specified by §gradle/FS-gradle-native-image-tasks and
§gradle/FS-gradle-native-image-invocation. Maven-specific goal inputs are specified by
§maven/FS-maven-goal-surface, §maven/FS-maven-native-image-builds, and
§maven/FS-maven-configuration-model.

## 2. Command-line construction

Both plugins must construct the `native-image` command line through shared utilities from
§common/FS-common-libraries.1 so escaping, quoting, and argument-file conversion stay identical.
Plugin-specific string handling must not bypass those utilities.

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
version before passing it to `native-image` (§FS-resources-and-metadata.5).

## 5. Shared library mode

Both plugins must support shared-library output where the build tool's packaging model allows it.
Shared-library mode disables entry-point requirements and may change the output file extension.
The plugin-specific configuration surface and defaults are specified by
§gradle/FS-gradle-plugin-model and §maven/FS-maven-configuration-model.
