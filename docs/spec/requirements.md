# REQ-001-backwards-compatibility-across-plugin-versions: Plugin upgrades keep existing Gradle and Maven builds working

This requirement constrains the goals under §GRUND-001-native-build-tools-reason-for-existence, in
particular §GOAL-001-build-tool-native-image-workflows. A project that upgrades to a newer plugin
version must keep building without forced changes to its build script. The durable Gradle DSL
surface in §FS-001-gradle-plugin-native-image-workflow.1.2 and the Maven goal and configuration
surface in §FS-002-maven-plugin-native-image-workflow.1 must stay compatible across minor and patch
releases.

## 1. Deprecation over removal

When behavior must change, the plugins keep a compatibility path rather than breaking existing
builds. Renamed tasks and goals retain deprecated aliases that delegate to the replacement and warn
users toward the current name, as required by §FS-001-gradle-plugin-native-image-workflow.2.3 and
§FS-002-maven-plugin-native-image-workflow.1.1. Removal happens only on a major version boundary
after a deprecation period.

## 2. Configuration compatibility

Existing DSL properties, extension settings, and Maven parameters keep their documented meaning
across compatible releases. New behavior is added through new, optional configuration with
conservative defaults, so builds that do not opt in are unaffected.
