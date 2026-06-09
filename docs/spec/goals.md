# GOAL-native-build-workflows: Gradle and Maven users can build and test native images through native build-tool workflows

The Gradle and Maven plugins expose native image compile, run, test, resource configuration,
agent metadata, and reachability metadata workflows through idioms that fit each build tool.
Realized by §gradle/GOAL-native-workflows,
§maven/GOAL-native-workflows, and §FS-plugin-common.
Bounded by the non-goals in [non-goals.md](non-goals.md) and constrained by
§REQ-plugin-compatibility and
§REQ-support-matrix and
§REQ-real-fixtures.

# GOAL-fresh-metadata: Users receive current GraalVM reachability metadata

Refines §GRUND-product-purpose. Native Build Tools should fetch and consume the latest compatible
GraalVM Reachability Metadata Repository by default so users benefit from current third-party
library metadata without vendoring Native Image configuration. The default must remain overridable
for reproducible builds, offline builds, local repositories, and deliberately pinned metadata
versions. Realized by §FS-resources-metadata.2, §common/FS-common-libraries.5,
§gradle/FS-resources-metadata.3, and §maven/FS-resources-metadata.2; constrained by
§REQ-support-matrix and §REQ-plugin-compatibility.

# GOAL-plugin-parity: Shared native-image behavior remains consistent across Gradle and Maven

Behavior both plugins expose lives in the cross-plugin product contract; build-tool-neutral
primitives live in common modules reused by both plugins. Covers native-image utilities, resource
model analysis, reachability metadata lookup, tracing-agent behavior, cross-plugin parity, and
JUnit native support. See §FS-plugin-common, §common/FS-common-libraries, and
§common/AR-common-libraries. Constrained by
§REQ-real-fixtures.

# GOAL-jvm-ecosystem-interop: Most of the JVM build ecosystem keeps working under Native Image

Refines §GRUND-product-purpose. Build-tool toolchains, common Gradle and
Maven plugins (`application`, `java-library`, `jacoco`, Kotlin, etc.), test frameworks, packaging
plugins, and developer tooling that JVM projects already rely on should continue to work when
Native Build Tools is added to the project. The plugins must integrate with the host build tool's
existing model rather than replacing it; when an ecosystem capability is structurally incompatible
with Native Image, that gap belongs in [non-goals.md](non-goals.md) with a documented reason.
Realized by §gradle/GOAL-native-workflows and
§maven/GOAL-native-workflows, bounded by
§NGOAL-no-buildtool-duplicates.

# GOAL-concise-actionable-output: Build output is concise, actionable, and token-efficient

Refines §GRUND-product-purpose. Default Gradle and Maven plugin output
should explain what the Native Build Tools integration did, where important artifacts or reports
were written, and what action a user should take next without flooding CI logs or agent transcripts.
Detailed diagnostics belong behind the build tool's normal verbose or debug output controls.

# GOAL-fast-feedback: Native build workflows provide feedback as fast as practical

Refines §GRUND-product-purpose. The plugins should minimize avoidable
configuration, dependency resolution, metadata processing, and repeated Native Image invocations
while preserving correctness and build-tool idioms. Fast feedback includes local developer builds,
functional tests, and CI validation paths, with expensive work declared as build inputs and outputs
where the build tool can skip, cache, or parallelize it.
