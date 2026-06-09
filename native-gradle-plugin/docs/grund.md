# GRUND-plugin-purpose: The Gradle plugin realizes Native Build Tools through Gradle idioms

The Gradle plugin exists so Gradle users can build, run, test, and configure GraalVM Native Image
artifacts without leaving Gradle's task, provider, toolchain, and extension model. It is the Gradle
subproject realization of [§root/GRUND-why-nbt](../../docs/spec/grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image) and the shared plugin
contract in [§root/FS-plugin-common](../../docs/spec/functional/plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior).

This subproject owns the Gradle-facing behavior in the focused specs under `docs/functional/`
and the implementation boundaries in [§AR-gradle-plugin](architecture.md#ar-gradle-plugin-the-gradle-plugin-adapts-shared-native-image-behavior-to-gradle-apis). Repository-wide non-goals still apply here, especially
[§root/NGOAL-no-flag-mirroring](../../docs/spec/non-goals.md#ngoal-no-flag-mirroring-the-plugins-do-not-add-build-tool-flags-that-only-forward-to-native-image-flags) and
[§root/NGOAL-no-buildtool-duplicates](../../docs/spec/non-goals.md#ngoal-no-buildtool-duplicates-the-plugins-do-not-reimplement-capabilities-that-gradle-or-maven-already-provide).
