# REQ-plugin-compatibility: Plugin upgrades keep existing Gradle and Maven builds working

This requirement constrains the goals under §GRUND-product-purpose, in
particular §GOAL-native-build-workflows. A project that upgrades to a newer plugin
version must keep building without forced changes to its build script. The durable Gradle DSL
surface in §gradle/FS-plugin-model.2 and the Maven goal and configuration surfaces in
§maven/FS-goal-surface and §maven/FS-config-model must stay compatible across
minor and patch releases.

## 1. Deprecation over removal

When behavior must change, the plugins keep a compatibility path rather than breaking existing
builds. Renamed tasks and goals retain deprecated aliases that delegate to the replacement and warn
users toward the current name, as required by §gradle/FS-native-tasks.3 and
§maven/FS-goal-surface.1. Removal happens only on a major version boundary
after a deprecation period.

## 2. Configuration compatibility

Existing DSL properties, extension settings, and Maven parameters keep their documented meaning
across compatible releases. New behavior is added through new, optional configuration with
conservative defaults, so builds that do not opt in are unaffected.

# REQ-support-matrix: Supported JDK, GraalVM, Gradle, and Maven versions are declared and tested

This requirement constrains the goals under §GRUND-product-purpose,
alongside §REQ-plugin-compatibility and §GOAL-fresh-metadata. Native Build Tools must declare and
test the supported JDK, GraalVM Native Image, build-tool, and reachability metadata lines.

## 1. Declared support

The plugins require Java 17+ to run. Native Image workflows must support JDK 25+. The Gradle plugin
supports Gradle 8.3+ and currently tests `current`, `8.4`, `8.14.2`, and `9.0.0`. The Maven plugin
supports Maven 3.9.x and currently tests with `3.9.9`. Metadata defaults must track the latest
compatible published repository while remaining pinnable by version, URI, or local path.

## 2. Changing support

Raising a floor or dropping a tested JDK, GraalVM, Gradle, Maven, or metadata repository line is
compatibility-relevant under §REQ-plugin-compatibility.2. Update the declaring build files,
CI/functional-test matrix, user documentation, and changelog together.

# REQ-real-fixtures: Samples and functional tests protect real build scenarios

This requirement constrains §GOAL-native-build-workflows and
§GOAL-plugin-parity. Executable samples, fixtures, and reproducers
stay close to the plugin code so changes can be verified against realistic Gradle and Maven
projects. They validate §FS-native-tests and related product behavior through the fixture ownership
described by §AR-build-infrastructure.4.
