# GRUND-gradle-plugin-purpose: The Gradle plugin realizes Native Build Tools through Gradle idioms

The Gradle plugin exists so Gradle users can build, run, test, and configure GraalVM Native Image
artifacts without leaving Gradle's task, provider, toolchain, and extension model. It is the Gradle
subproject realization of §root/GRUND-native-build-tools-reason-for-existence and the shared plugin
contract in §root/FS-plugin-common-behavior.

This subproject owns the Gradle-facing behavior in the focused specs under `docs/functional/`
and the implementation boundaries in §AR-gradle-plugin. Repository-wide non-goals still apply here, especially
§root/NGOAL-no-build-tool-flags-for-native-image-flags and
§root/NGOAL-no-duplication-of-existing-build-tool-capabilities.
