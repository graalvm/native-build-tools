# FS-003-metadata-and-resource-workflows: The plugins support resource config, agent metadata, and reachability metadata workflows

Native Build Tools must help users supply the configuration that Native Image needs for resources,
reflection, dynamic access, and third-party library reachability metadata. The behavior is shared
where possible and adapted into Gradle tasks and Maven mojos where build-tool integration differs.

## Required behavior

- Analyze classpath entries and resource patterns to produce Native Image resource configuration.
- Support direct, standard, conditional, and disabled agent modes for collecting configuration from
  JVM executions.
- Merge and copy agent-generated metadata into user-selected locations.
- Resolve GraalVM reachability metadata repository entries for project dependencies and report
  libraries that appear to be missing metadata.
- Validate metadata-oriented JSON against the schemas owned by this repository where applicable.

## Verification surface

Common utility and reachability metadata modules have unit tests for parsing, scanning, repository
lookup, and missing metadata support. Product plugin functional tests cover the same behavior in
Gradle and Maven sample projects.
