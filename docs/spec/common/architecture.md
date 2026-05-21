# AR-003-shared-common-libraries: Common modules own build-tool-neutral Native Image support

The `common/` directory contains libraries that should remain independent from Gradle and Maven
plugin APIs unless a dependency is explicitly part of that library's role.

## Modules

- `common/utils` owns shared agent configuration models, resource configuration models,
  classpath and JAR analysis, native-image utility behavior, schema validation helpers, and shared
  constants.
- `common/graalvm-reachability-metadata` owns metadata repository access, artifact and module
  indexes, directory configuration, query objects, and missing metadata command support.
- `common/junit-platform-native` owns JUnit Platform support that is needed inside native test
  images, including native-image feature registration, test class registration, launch behavior,
  and JUnit configuration providers.

## Dependency direction

Product plugins may depend on common modules. Common modules should not depend on product plugin
implementation classes. This keeps §GOAL-002-shared-native-image-behavior-stays-consistent
achievable without making Gradle and Maven integration code mutually dependent.
