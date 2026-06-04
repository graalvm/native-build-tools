# REQ-backwards-compatibility-across-plugin-versions: Plugin upgrades keep existing Gradle and Maven builds working

This requirement constrains the goals under §GRUND-native-build-tools-reason-for-existence, in
particular §GOAL-native-build-workflows. A project that upgrades to a newer plugin
version must keep building without forced changes to its build script. The durable Gradle DSL
surface in §gradle/FS-gradle-plugin-model.2 and the Maven goal and configuration surfaces in
§maven/FS-maven-goal-surface and §maven/FS-maven-configuration-model must stay compatible across
minor and patch releases.

## 1. Deprecation over removal

When behavior must change, the plugins keep a compatibility path rather than breaking existing
builds. Renamed tasks and goals retain deprecated aliases that delegate to the replacement and warn
users toward the current name, as required by §gradle/FS-gradle-native-image-tasks.3 and
§maven/FS-maven-goal-surface.1. Removal happens only on a major version boundary
after a deprecation period.

## 2. Configuration compatibility

Existing DSL properties, extension settings, and Maven parameters keep their documented meaning
across compatible releases. New behavior is added through new, optional configuration with
conservative defaults, so builds that do not opt in are unaffected.

# REQ-supported-build-tool-and-runtime-version-matrix: Supported JDK, GraalVM, Gradle, and Maven versions are declared and tested

This requirement constrains the goals under §GRUND-native-build-tools-reason-for-existence,
alongside §REQ-backwards-compatibility-across-plugin-versions. Native Build Tools integrates
three moving external surfaces — the JDK, the GraalVM Native Image distribution, and the host build
tool — so the project must declare the supported version floors or ranges and exercise them in CI
rather than leaving compatibility implicit.

## 1. Source of truth

Supported versions must be read from the build logic, version catalog, workflow configuration, and
functional-test matrices that execute the project. The specification cites the compatibility
contract, but it must not duplicate exact version floors unless a value is part of a deliberate
compatibility decision.

## 2. Changing a supported version

Raising a floor or dropping a tested JDK, GraalVM, Gradle, or Maven version is a
compatibility-relevant change under §REQ-backwards-compatibility-across-plugin-versions.2. Update
the declaring build files, CI or functional-test matrix, user documentation, and changelog together
so downstream builds are not surprised.

# REQ-repository-fixtures-protect-real-build-scenarios: Samples and functional tests protect real build scenarios

This requirement constrains §GOAL-native-build-workflows and
§GOAL-plugin-parity. Executable samples, fixtures, and reproducers
stay close to the plugin code so changes can be verified against realistic Gradle and Maven
projects. They validate §FS-native-tests and related product behavior through the fixture ownership
described by §AR-build-infrastructure.4.
