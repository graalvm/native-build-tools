# REQ-backwards-compatibility-across-plugin-versions: Plugin upgrades keep existing Gradle and Maven builds working

This requirement constrains the goals under §GRUND-native-build-tools-reason-for-existence, in
particular §GOAL-build-tool-native-image-workflows. A project that upgrades to a newer plugin
version must keep building without forced changes to its build script. The durable Gradle DSL
surface in §gradle/FS-gradle-plugin.1.2 and the Maven goal and configuration
surface in §maven/FS-maven-plugin.1 must stay compatible across minor and patch
releases.

## 1. Deprecation over removal

When behavior must change, the plugins keep a compatibility path rather than breaking existing
builds. Renamed tasks and goals retain deprecated aliases that delegate to the replacement and warn
users toward the current name, as required by §gradle/FS-gradle-plugin.2.3 and
§maven/FS-maven-plugin.1.1. Removal happens only on a major version boundary
after a deprecation period.

## 2. Configuration compatibility

Existing DSL properties, extension settings, and Maven parameters keep their documented meaning
across compatible releases. New behavior is added through new, optional configuration with
conservative defaults, so builds that do not opt in are unaffected.

# REQ-supported-build-tool-and-runtime-version-matrix: The plugins declare and test a supported JDK, GraalVM, Gradle, and Maven version matrix

This requirement constrains the goals under §GRUND-native-build-tools-reason-for-existence,
alongside §REQ-backwards-compatibility-across-plugin-versions. Native Build Tools integrates
three moving external surfaces — the JDK, the GraalVM Native Image distribution, and the host build
tool — so the project must declare which versions it supports and exercise them in CI rather than
leaving compatibility implicit.

## 1. Current support matrix

Supported versions are declared in the build and CI, not in this document, so that code stays the
source of truth. As of plugin version 1.1.2 the matrix is:

| Surface | Supported | Declared in |
| --- | --- | --- |
| JDK | 17 or later | `build-logic/common-plugins/src/main/kotlin/org.graalvm.build.java.gradle.kts` (`JavaLanguageVersion.of(17)`) |
| GraalVM (Native Image) | 23.0.2 build line | `gradle/libs.versions.toml` (`graalvm`) |
| Gradle | tested floor 8.4 through 9.x | `build-logic/gradle-functional-testing/src/main/groovy/org.graalvm.build.functional-testing.gradle` |
| Maven | 3.9.9 or later | `gradle/libs.versions.toml` (`maven`) |

These values move over time; treat the cited files as authoritative and update this table when a
floor changes.

## 2. Changing a supported version

Raising a floor — dropping support for an old JDK, GraalVM, Gradle, or Maven version — is a
compatibility-relevant change under §REQ-backwards-compatibility-across-plugin-versions.2 and
must be deliberate. Update the declaring build files and the functional-test matrix that exercises
the range together, refresh the user documentation, and call out the change in the changelog so
downstream builds are not surprised.
