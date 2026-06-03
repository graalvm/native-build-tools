# GOAL-native-build-workflows: Gradle and Maven users can build and test native images through native build-tool workflows

The Gradle and Maven plugins expose native image compile, run, test, resource configuration,
agent metadata, and reachability metadata workflows through idioms that fit each build tool.
Realized by §gradle/FS-gradle-plugin, §maven/FS-maven-plugin, and §FS-plugin-common-behavior.
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
