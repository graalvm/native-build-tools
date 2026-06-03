# NGOAL-no-build-tool-flags-for-native-image-flags: The plugins do not add build-tool flags that only forward to native-image flags

This non-goal bounds §GOAL-native-build-workflows under the grounding in
§GRUND-native-build-tools-reason-for-existence. The Gradle and Maven plugins must not introduce
a DSL option, extension property, or goal parameter whose only purpose is to forward a value to an
existing `native-image` flag. Such flags are already reachable through the build-argument
pass-through: §gradle/FS-gradle-plugin.2.4 for Gradle build args and
§maven/FS-maven-plugin.3.1 for Maven `<buildArgs>`. Mirroring every
`native-image` flag in the build tools would couple the plugins to the native-image command-line
surface and grow the maintenance and documentation burden without adding capability. Proposals to
add such a flag are declined and redirected to the pass-through.

**Exception — developer feedback flags.** A first-class option may forward to a single
`native-image` flag when it materially improves developer iteration speed and serves
§GOAL-fast-feedback. The quick-build toggle that forwards to `-Ob`
(§gradle/FS-gradle-plugin.2.4 `quickBuild`, Maven `<quickBuild>`) is the canonical example: it is
the flag developers reach for most often during local iteration, so making it discoverable in the
DSL/XML pays for its maintenance cost. New flags claiming this exception must cite which
developer-feedback workflow they accelerate.

# NGOAL-no-duplication-of-existing-build-tool-capabilities: The plugins do not reimplement capabilities that Gradle or Maven already provide

The plugins must not add features that users can already achieve with existing Gradle or Maven
capabilities, unless the multi-target nature of Native Image makes the existing capability
excessively complex to use. This keeps the plugins focused on Native Image integration rather than
on general build orchestration that the host build tool already owns. When a proposed feature
overlaps an existing build-tool capability, accept it only when Native Image's multiple build
targets — for example the main, test, and custom binaries of
§gradle/FS-gradle-plugin.1.4 — would otherwise force users into excessive
boilerplate or error-prone configuration.
