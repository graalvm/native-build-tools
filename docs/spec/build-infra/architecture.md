# AR-005-build-documentation-and-release-infrastructure: Repository infrastructure is separate from product runtime behavior

The repository contains internal infrastructure that exists to build, test, document, and release
the product plugins, but is not itself the product surface.

## Build logic

`build-logic/` owns internal Gradle convention plugins for settings, Java conventions,
publishing, documentation, functional testing, reachability metadata fetching, utility module
generation, aggregation, and Git-related release tasks.

## Documentation

`docs/` owns the generated user documentation sources and the grund specification. The existing
AsciiDoc tree under `docs/src/docs/asciidoc/` remains the user guide source, while the Markdown
files created by grund provide maintainer-facing specification anchors.

## Samples and fixtures

`samples/`, `test-support/`, plugin test fixtures, and Maven reproducers should remain focused on
verifying concrete Gradle and Maven scenarios. They are evidence for the product behavior described
by §FS-001-gradle-plugin-native-image-workflow, §FS-002-maven-plugin-native-image-workflow,
§FS-004-native-test-execution, and §AR-004-samples-and-functional-fixtures.
