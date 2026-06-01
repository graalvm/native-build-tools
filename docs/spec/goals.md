# GOAL-build-tool-native-image-workflows: Gradle and Maven users can build and test native images through native build-tool workflows

This goal follows the project grounding in §GRUND-native-build-tools-reason-for-existence.
The Gradle and Maven plugins are the product surface of this repository. They must expose native
image compile, run, test, resource configuration, agent metadata, and reachability metadata
workflows through idioms that fit each build tool. This goal is realized by §gradle/FS-gradle-plugin,
§maven/FS-maven-plugin, §FS-plugin-common-behavior, §FS-common-libraries, and
§FS-plugin-common-behavior.3. It is bounded by §NGOAL-no-build-tool-flags-for-native-image-flags and
§NGOAL-no-duplication-of-existing-build-tool-capabilities, and constrained by
§REQ-backwards-compatibility-across-plugin-versions and
§REQ-supported-build-tool-and-runtime-version-matrix.

# GOAL-shared-native-image-behavior-stays-consistent: Shared native-image behavior remains consistent across Gradle and Maven

Behavior that both product plugins should expose belongs in the cross-plugin product contract.
Build-tool-neutral implementation primitives should live in common modules and be reused by both
plugins. The shared layer covers native-image utility behavior, resource model analysis,
reachability metadata lookup, tracing-agent behavior, cross-plugin parity, and JUnit native
support. The relevant specs are §FS-plugin-common-behavior, §FS-common-libraries, and
§AR-common-libraries.

# GOAL-repository-fixtures-protect-real-build-scenarios: Samples and functional tests protect real build scenarios

The repository must keep executable samples, fixtures, and reproducers close to the plugin code so
changes can be verified against realistic Gradle and Maven projects. These scenarios provide the
practical validation path for §FS-plugin-common-behavior.3 and
§AR-build-infrastructure.4.
