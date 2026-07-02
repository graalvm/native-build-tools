# REQ-plugin-compatibility: The plugins remain compatible with the Gradle and Maven host build tools

This requirement constrains the goals under [§GRUND-why-nbt](grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image), in particular
[§GOAL-jvm-ecosystem-interop](goals.md#goal-jvm-ecosystem-interop-most-of-the-jvm-build-ecosystem-keeps-working-under-native-image) and [§GOAL-native-build-workflows](goals.md#goal-native-build-workflows-users-can-easily-build-run-and-test-native-images-with-their-build-tool). The Gradle and Maven plugins
must build on the public, stable APIs of their host build tools and extend the host configuration
model rather than replacing it, so that supported releases of Gradle and Maven continue to work
with Native Build Tools. The supported host lines are declared and tested under
[§REQ-support-matrix](requirements.md#req-support-matrix-supported-jdk-graalvm-gradle-and-maven-versions-are-declared-and-tested).

# REQ-backwards-compatibility: Plugin upgrades keep existing Gradle and Maven builds working

This requirement constrains the goals under [§GRUND-why-nbt](grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image), in
particular [§GOAL-native-build-workflows](goals.md#goal-native-build-workflows-users-can-easily-build-run-and-test-native-images-with-their-build-tool). A project that upgrades to a newer plugin
version must keep building without forced changes to its build script. The durable Gradle DSL
surface in [§gradle/FS-plugin-model.2](../../native-gradle-plugin/docs/functional/plugin-model.md#2-extension-surface) and the Maven goal and configuration surfaces in
[§maven/FS-goal-surface](../../native-maven-plugin/docs/functional/goal-surface.md#fs-goal-surface-maven-goals-expose-native-image-workflows) and [§maven/FS-config-model](../../native-maven-plugin/docs/functional/configuration-model.md#fs-config-model-maven-xml-and-command-line-properties-configure-native-image-builds) must stay compatible across minor and patch releases.

## 1. Deprecation over removal

When behavior must change, the plugins keep a compatibility path rather than breaking existing
builds. Renamed tasks and goals retain deprecated aliases that delegate to the replacement and warn
users toward the current name, as required by [§gradle/FS-native-tasks.3](../../native-gradle-plugin/docs/functional/native-image-tasks.md#3-deprecated-task-aliases) and
[§maven/FS-goal-surface.1](../../native-maven-plugin/docs/functional/goal-surface.md#1-build-goals). Removal happens only on a major version boundary
after a deprecation period.

## 2. Configuration compatibility

Existing DSL properties, extension settings, and Maven parameters keep their documented meaning
across compatible releases. New behavior is added through new, optional configuration with
conservative defaults, so builds that do not opt in are unaffected.

# REQ-support-matrix: Supported JDK, GraalVM, Gradle, and Maven versions are declared and tested

This requirement constrains the goals under [§GRUND-why-nbt](grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image),
alongside [§REQ-plugin-compatibility](requirements.md#req-plugin-compatibility-the-plugins-remain-compatible-with-the-gradle-and-maven-host-build-tools), [§REQ-backwards-compatibility](requirements.md#req-backwards-compatibility-plugin-upgrades-keep-existing-gradle-and-maven-builds-working), and [§GOAL-fresh-metadata](goals.md#goal-fresh-metadata-users-can-fetch-the-latest-graalvm-reachability-metadata). Native Build Tools
must declare and test the supported JDK, GraalVM Native Image, build-tool, and reachability
metadata lines.

## 1. Declared support

The plugins require Java 17+ to run. Native Image workflows must support JDK 25+, and are tested
against the two most recent GraalVM LTS versions. The Gradle plugin
supports Gradle 8.3+ and currently tests `current`, `8.4`, `8.14.2`, `9.0.0`, and `9.6.1`. The Maven plugin
supports Maven 3.9.x and currently tests with `3.9.9`. Metadata defaults must track the latest
compatible published repository while remaining pinnable by version, URI, or local path.

## 2. Changing support

Raising a floor or dropping a tested JDK, GraalVM, Gradle, Maven, or metadata repository line is
compatibility-relevant under [§REQ-backwards-compatibility.2](requirements.md#2-configuration-compatibility). Update the declaring build files,
CI/functional-test matrix, user documentation, and changelog together.

# REQ-real-fixtures: Samples and functional tests protect real build scenarios

This requirement constrains [§GOAL-native-build-workflows](goals.md#goal-native-build-workflows-users-can-easily-build-run-and-test-native-images-with-their-build-tool) and
[§GOAL-plugin-parity](goals.md#goal-plugin-parity-shared-native-image-behavior-remains-consistent-across-gradle-and-maven). Executable samples, fixtures, and reproducers
stay close to the plugin code so changes can be verified against realistic Gradle and Maven
projects. They validate [§FS-native-tests](functional/native-tests.md#fs-native-tests-both-plugins-compile-and-execute-junit-tests-as-a-native-image) and related product behavior through the fixture ownership
described by [§AR-build-infrastructure.4](architecture/build-infrastructure.md#4-fixture-and-sample-boundary).
