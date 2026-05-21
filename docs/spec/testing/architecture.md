# AR-004-samples-and-functional-fixtures: Samples and fixtures preserve realistic build-tool scenarios

The repository's samples and fixtures are part of the specification surface because the product
plugins are only useful when they work in real Gradle and Maven project shapes. This supports
§GOAL-003-repository-fixtures-protect-real-build-scenarios.

## Fixture groups

- `samples/` contains example projects for Java applications, Java libraries, resources,
  reflection, custom tests, custom source sets, Kotlin tests, multi-project builds, metadata
  repository integration, native config integration, and layered application behavior.
- `test-support/` contains reusable artifacts that product plugin functional tests can publish
  into the common test repository.
- `native-maven-plugin/reproducers/` contains Maven issue reproducers when a regression needs a
  dedicated project shape.

These fixtures provide verification evidence for §FS-001-gradle-plugin-native-image-workflow,
§FS-002-maven-plugin-native-image-workflow, §FS-004-native-test-execution, and
§FS-003-metadata-and-resource-workflows.
