# GOAL-native-build-workflows: Users can easily build, run, and test native images with their build-tool

The Gradle and Maven plugins expose native image compile, run, test, agent metadata collection,
and reachability metadata workflows through idioms that fit each build tool.
Realized by [§gradle/GOAL-native-workflows](../../native-gradle-plugin/docs/goals.md#goal-native-workflows-gradle-users-can-use-native-image-through-gradle-native-workflows),
[§maven/GOAL-native-workflows](../../native-maven-plugin/docs/goals.md#goal-native-workflows-maven-users-can-use-native-image-through-maven-native-workflows), and [§FS-plugin-common](functional/plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior).
Bounded by the non-goals in [non-goals.md](non-goals.md) and constrained by
[§REQ-plugin-compatibility](requirements.md#req-plugin-compatibility-the-plugins-remain-compatible-with-the-gradle-and-maven-host-build-tools),
[§REQ-backwards-compatibility](requirements.md#req-backwards-compatibility-plugin-upgrades-keep-existing-gradle-and-maven-builds-working),
[§REQ-support-matrix](requirements.md#req-support-matrix-supported-jdk-graalvm-gradle-and-maven-versions-are-declared-and-tested), and
[§REQ-real-fixtures](requirements.md#req-real-fixtures-samples-and-functional-tests-protect-real-build-scenarios).

# GOAL-fresh-metadata: Users can fetch the latest GraalVM reachability metadata

Refines [§GRUND-why-nbt](grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image). Native Build Tools should fetch and consume the latest compatible
GraalVM Reachability Metadata Repository by default so users benefit from current third-party
library metadata without vendoring Native Image configuration. The default must remain overridable
for reproducible builds, offline builds, local repositories, and deliberately pinned metadata
versions. Realized by [§FS-resources-and-metadata.2](functional/resources-and-metadata.md#2-reachability-metadata-repository), [§common/FS-common-libraries.5](../../common/docs/functional-spec.md#5-reachability-metadata-repository),
[§gradle/FS-resources-and-metadata.3](../../native-gradle-plugin/docs/functional/resources-and-metadata.md#3-reachability-metadata-collection), and [§maven/FS-resources-and-metadata.2](../../native-maven-plugin/docs/functional/resources-and-metadata.md#2-reachability-metadata); constrained by
[§REQ-support-matrix](requirements.md#req-support-matrix-supported-jdk-graalvm-gradle-and-maven-versions-are-declared-and-tested) and [§REQ-backwards-compatibility](requirements.md#req-backwards-compatibility-plugin-upgrades-keep-existing-gradle-and-maven-builds-working).

# GOAL-plugin-parity: Shared native-image behavior remains consistent across Gradle and Maven

Behavior both plugins expose lives in the cross-plugin product contract; build-tool-neutral
primitives live in common modules reused by both plugins. Covers native-image utilities, resource
model analysis, reachability metadata lookup, tracing-agent behavior, cross-plugin parity, and
JUnit native support. See [§FS-plugin-common](functional/plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior), [§common/FS-common-libraries](../../common/docs/functional-spec.md#fs-common-libraries-common-libraries-provide-shared-native-image-utilities-and-metadata-workflows), and
[§common/AR-common-libraries](../../common/docs/architecture.md#ar-common-libraries-shared-libraries-stay-independent-from-gradle-and-maven-apis). Constrained by
[§REQ-real-fixtures](requirements.md#req-real-fixtures-samples-and-functional-tests-protect-real-build-scenarios).

# GOAL-jvm-ecosystem-interop: Most of the JVM build ecosystem keeps working under Native Image

Refines [§GRUND-why-nbt](grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image). Build-tool toolchains, common Gradle and
Maven plugins (`application`, `java-library`, `jacoco`, Kotlin, etc.), test frameworks, packaging
plugins, and developer tooling that JVM projects already rely on should continue to work when
Native Build Tools is added to the project. The plugins must integrate with the host build tool's
existing model rather than replacing it; when an ecosystem capability is structurally incompatible
with Native Image, that gap belongs in [non-goals.md](non-goals.md) with a documented reason.
Realized by [§gradle/GOAL-native-workflows](../../native-gradle-plugin/docs/goals.md#goal-native-workflows-gradle-users-can-use-native-image-through-gradle-native-workflows) and
[§maven/GOAL-native-workflows](../../native-maven-plugin/docs/goals.md#goal-native-workflows-maven-users-can-use-native-image-through-maven-native-workflows), bounded by
[§NGOAL-no-buildtool-duplicates](non-goals.md#ngoal-no-buildtool-duplicates-the-plugins-do-not-reimplement-capabilities-that-gradle-or-maven-already-provide).

# GOAL-concise-actionable-output: Build output is concise, actionable, and token-efficient

Refines [§GRUND-why-nbt](grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image). Default Gradle and Maven plugin output
should explain what the Native Build Tools integration did, where important artifacts or reports
were written, and what action a user should take next without flooding CI logs or agent transcripts.
Detailed diagnostics belong behind the build tool's normal verbose or debug output controls.

# GOAL-fast-feedback: Native build workflows provide feedback as fast as practical

Refines [§GRUND-why-nbt](grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image). The plugins should minimize avoidable
configuration, dependency resolution, metadata processing, and repeated Native Image invocations
while preserving correctness and build-tool idioms. Fast feedback includes local developer builds,
functional tests, and CI validation paths, with expensive work declared as build inputs and outputs
where the build tool can skip, cache, or parallelize it.
