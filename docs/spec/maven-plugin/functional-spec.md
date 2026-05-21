# FS-002-maven-plugin-native-image-workflow: The Maven plugin wires Native Image behavior into Maven builds

The `native-maven-plugin` module provides a Maven plugin packaged as `maven-plugin`. Its mojos
adapt Native Image build, test, resource, metadata, and support workflows into Maven's lifecycle,
configuration, plugin descriptor, and repository model.

## Required behavior

- Provide mojos for native compilation, no-fork native compilation, native testing, resource
  configuration generation, dynamic access metadata, reachability metadata, metadata copy, agent
  merge, missing metadata listing, and argument file writing.
- Support Maven-specific configuration objects for metadata repositories, agent modes, exclusion
  configuration, and metadata copying.
- Seed a local Maven repository for functional tests so plugin behavior can be exercised against
  realistic Maven sample projects.
- Keep shared native-image and metadata behavior aligned with the Gradle plugin through the common
  modules described in §AR-003-shared-common-libraries.

## Verification surface

The module's unit tests cover mojos and configuration objects. Its functional tests execute Maven
sample projects, issue reproducers, SBOM behavior, metadata repository integration, and native test
execution scenarios.
