# GOAL-001-build-tool-native-image-workflows: Gradle and Maven users can build and test native images through native build-tool workflows

This goal follows the project grounding in §GRUND-001-native-build-tools-reason-for-existence.
The Gradle and Maven plugins are the product surface of this repository. They must expose native
image compile, run, test, resource configuration, agent metadata, and reachability metadata
workflows through idioms that fit each build tool. This goal is realized by
§FS-001-gradle-plugin-native-image-workflow and §FS-002-maven-plugin-native-image-workflow. It is
bounded by §NGOAL-001-no-build-tool-flags-for-native-image-flags and
§NGOAL-002-no-duplication-of-existing-build-tool-capabilities, and constrained by
§REQ-001-backwards-compatibility-across-plugin-versions.

# GOAL-002-shared-native-image-behavior-stays-consistent: Shared native-image behavior remains consistent across Gradle and Maven

Behavior that is not inherently tied to a build tool should live in common modules and be reused
by both plugins. The shared layer covers native-image utility behavior, resource model analysis,
reachability metadata lookup, and JUnit native support. The relevant specs are
§REPO-001-module-boundaries and §AR-003-shared-common-libraries.

# GOAL-003-repository-fixtures-protect-real-build-scenarios: Samples and functional tests protect real build scenarios

The repository must keep executable samples, fixtures, and reproducers close to the plugin code so
changes can be verified against realistic Gradle and Maven projects. These scenarios provide the
practical validation path for §FS-004-native-test-execution and
§AR-004-samples-and-functional-fixtures.
