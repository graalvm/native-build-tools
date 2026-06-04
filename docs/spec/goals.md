# GOAL-native-build-workflows: Gradle and Maven users can build and test native images through native build-tool workflows

The Gradle and Maven plugins expose native image compile, run, test, resource configuration,
agent metadata, and reachability metadata workflows through idioms that fit each build tool.
Realized by §gradle/GOAL-gradle-plugin-native-image-workflows,
§maven/GOAL-maven-plugin-native-image-workflows, and §FS-plugin-common-behavior.
Bounded by the non-goals in [non-goals.md](non-goals.md) and constrained by
§REQ-backwards-compatibility-across-plugin-versions and
§REQ-supported-build-tool-and-runtime-version-matrix and
§REQ-repository-fixtures-protect-real-build-scenarios.

# GOAL-plugin-parity: Shared native-image behavior remains consistent across Gradle and Maven

Behavior both plugins expose lives in the cross-plugin product contract; build-tool-neutral
primitives live in common modules reused by both plugins. Covers native-image utilities, resource
model analysis, reachability metadata lookup, tracing-agent behavior, cross-plugin parity, and
JUnit native support. See §FS-plugin-common-behavior, §common/FS-common-libraries, and
§common/AR-common-libraries. Constrained by
§REQ-repository-fixtures-protect-real-build-scenarios.

# GOAL-jvm-ecosystem-interop: Most of the JVM build ecosystem keeps working under Native Image

Refines §GRUND-native-build-tools-reason-for-existence. Build-tool toolchains, common Gradle and
Maven plugins (`application`, `java-library`, `jacoco`, Kotlin, etc.), test frameworks, packaging
plugins, and developer tooling that JVM projects already rely on should continue to work when
Native Build Tools is added to the project. The plugins must integrate with the host build tool's
existing model rather than replacing it; when an ecosystem capability is structurally incompatible
with Native Image, that gap belongs in [non-goals.md](non-goals.md) with a documented reason.
Realized by §gradle/GOAL-gradle-plugin-native-image-workflows and
§maven/GOAL-maven-plugin-native-image-workflows, bounded by
§NGOAL-no-duplication-of-existing-build-tool-capabilities.

# GOAL-concise-actionable-output: Build output is concise, actionable, and token-efficient

Refines §GRUND-native-build-tools-reason-for-existence. Default Gradle and Maven plugin output
should explain what the Native Build Tools integration did, where important artifacts or reports
were written, and what action a user should take next without flooding CI logs or agent transcripts.
Detailed diagnostics belong behind the build tool's normal verbose or debug output controls.

# GOAL-fast-feedback: Native build workflows provide feedback as fast as practical

Refines §GRUND-native-build-tools-reason-for-existence. The plugins should minimize avoidable
configuration, dependency resolution, metadata processing, and repeated Native Image invocations
while preserving correctness and build-tool idioms. Fast feedback includes local developer builds,
functional tests, and CI validation paths, with expensive work declared as build inputs and outputs
where the build tool can skip, cache, or parallelize it.
