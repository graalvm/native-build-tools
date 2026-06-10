# GRUND-plugin-purpose: The Maven plugin realizes Native Build Tools through Maven idioms

The Maven plugin exists so Maven users can build, test, and configure GraalVM Native Image
artifacts through Maven goals, lifecycle bindings, XML configuration, system properties,
dependency scopes, and toolchains. It is the Maven subproject realization of
[§root/GRUND-why-nbt](../../docs/spec/grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image) and the shared plugin contract in
[§root/FS-plugin-common](../../docs/spec/functional/plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior).

This subproject owns the Maven-facing behavior in the focused specs under `docs/functional/`
and the implementation boundaries in [§AR-maven-plugin](architecture.md#ar-maven-plugin-the-maven-plugin-adapts-shared-native-image-behavior-to-maven-apis). Repository-wide non-goals still apply here, especially
[§root/NGOAL-no-flag-mirroring](../../docs/spec/non-goals.md#ngoal-no-flag-mirroring-the-plugins-do-not-add-build-tool-flags-that-only-forward-to-native-image-flags) and
[§root/NGOAL-no-buildtool-duplicates](../../docs/spec/non-goals.md#ngoal-no-buildtool-duplicates-the-plugins-do-not-reimplement-capabilities-that-gradle-or-maven-already-provide).
