# NGOAL-no-flag-mirroring: The plugins do not add build-tool flags that only forward to native-image flags

This non-goal bounds [§GOAL-native-build-workflows](goals.md#goal-native-build-workflows-users-can-easily-build-run-and-test-native-images-with-their-build-tool) under the grounding in
[§GRUND-why-nbt](grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image). The Gradle and Maven plugins must not introduce
a DSL option, extension property, or goal parameter whose only purpose is to forward a value to an
existing `native-image` flag. Such flags are already reachable through the build-argument
pass-through: [§gradle/FS-native-tasks.4](../../native-gradle-plugin/docs/functional/native-image-tasks.md#4-command-line-overrides) for Gradle build args and
[§maven/FS-config-model.1](../../native-maven-plugin/docs/functional/configuration-model.md#1-native-image-options) for Maven `<buildArgs>`. Mirroring every
`native-image` flag in the build tools would couple the plugins to the native-image command-line
surface and grow the maintenance and documentation burden without adding capability. Proposals to
add such a flag are declined and redirected to the pass-through.

**Exception — developer feedback flags.** A first-class option may forward to a single
`native-image` flag when it materially improves developer iteration speed and serves
[§GOAL-fast-feedback](goals.md#goal-fast-feedback-native-build-workflows-provide-feedback-as-fast-as-practical). The quick-build toggle that forwards to `-Ob`
([§gradle/FS-native-tasks.4](../../native-gradle-plugin/docs/functional/native-image-tasks.md#4-command-line-overrides) `quickBuild`, Maven `<quickBuild>`) is the canonical example: it is
the flag developers reach for most often during local iteration, so making it discoverable in the
DSL/XML pays for its maintenance cost. New flags claiming this exception must cite which
developer-feedback workflow they accelerate.

# NGOAL-no-buildtool-duplicates: The plugins do not reimplement capabilities that Gradle or Maven already provide

The plugins must not add features that users can already achieve with existing Gradle or Maven
capabilities, unless the multi-target nature of Native Image makes the existing capability
excessively complex to use. This keeps the plugins focused on Native Image integration rather than
on general build orchestration that the host build tool already owns. When a proposed feature
overlaps an existing build-tool capability, accept it only when Native Image's multiple build
targets — for example the main, test, and custom binaries of
[§gradle/FS-plugin-model.4](../../native-gradle-plugin/docs/functional/plugin-model.md#4-custom-binaries) — would otherwise force users into excessive
boilerplate or error-prone configuration.

# NGOAL-graalvm-is-graalvm: GraalVM constraints and bugs are not a matter of build tools

Native Build Tools does not work around GraalVM behavior. Documented constraints of `native-image`
are accepted as-is and surfaced to users rather than masked in the plugins. Bugs in `native-image`
are reported and fixed upstream in GraalVM, not patched around in the build-tool layer. This keeps
the plugins a thin integration surface and avoids accumulating compensating logic that drifts as
GraalVM evolves.
