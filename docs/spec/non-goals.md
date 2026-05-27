# NGOAL-001-no-build-tool-flags-for-native-image-flags: The plugins do not add build-tool flags that only forward to native-image flags

This non-goal bounds §GOAL-001-build-tool-native-image-workflows under the grounding in
§GRUND-001-native-build-tools-reason-for-existence. The Gradle and Maven plugins must not introduce
a DSL option, extension property, or goal parameter whose only purpose is to forward a value to an
existing `native-image` flag. Such flags are already reachable through the build-argument
pass-through: §FS-001-gradle-plugin-native-image-workflow.2.4 for Gradle build args and
§FS-002-maven-plugin-native-image-workflow.3.1 for Maven `<buildArgs>`. Mirroring every
`native-image` flag in the build tools would couple the plugins to the native-image command-line
surface and grow the maintenance and documentation burden without adding capability. Proposals to
add such a flag are declined and redirected to the pass-through.

# NGOAL-002-no-duplication-of-existing-build-tool-capabilities: The plugins do not reimplement capabilities that Gradle or Maven already provide

The plugins must not add features that users can already achieve with existing Gradle or Maven
capabilities, unless the multi-target nature of Native Image makes the existing capability
excessively complex to use. This keeps the plugins focused on Native Image integration rather than
on general build orchestration that the host build tool already owns. When a proposed feature
overlaps an existing build-tool capability, accept it only when Native Image's multiple build
targets — for example the main, test, and custom binaries of
§FS-001-gradle-plugin-native-image-workflow.1.4 — would otherwise force users into excessive
boilerplate or error-prone configuration.
