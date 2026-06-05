# GRUND-native-build-tools-reason-for-existence: Native Build Tools gives JVM projects build-tool-native workflows for GraalVM Native Image

Building Native Image applications requires special handling of dependencies, testing, the
native-image agent, and reachability metadata pulled from the GraalVM Reachability Metadata
Repository that ordinary Gradle and Maven workflows do not provide. Native Build Tools removes
that burden: its Gradle and Maven plugins wire metadata-repo lookups, resource configuration,
native JUnit execution, and agent runs into the build tools JVM projects already use. Goals are
refined in §GOAL-native-build-workflows,
§GOAL-plugin-parity,
§GOAL-jvm-ecosystem-interop,
§GOAL-concise-actionable-output, and §GOAL-fast-feedback; module
ownership is described by §AR-repository-architecture.

## Audiences

- JVM application and library authors who want Native Image support without leaving Gradle or
  Maven.
- Framework and library maintainers who need reachability metadata and native test support
  exercised in real build-tool scenarios.
