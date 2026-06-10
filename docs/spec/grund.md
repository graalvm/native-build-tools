# GRUND-why-nbt: Native Build Tools gives JVM projects build-tool-native workflows for GraalVM Native Image

Building Native Image applications requires special handling:

1. Dependencies are passed to `native-image` at build time.
2. Tests run by first building an executable and then executing the binary.
3. The `native-image` agent requires extra steps to process metadata.
4. `native-image` requires [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) pulled from the [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata).

Ordinary Gradle and Maven workflows cannot easily accommodate these steps.

Native Build Tools removes that burden: its Gradle and Maven plugins wire metadata-repo lookups, native JUnit execution, and agent runs into the build tools JVM projects already use. This project has the following [§GOAL]s(goals.md) and [requirements](requirements.md).

## Audiences

- JVM application and library authors who want Native Image support without leaving Gradle or Maven.
- Framework and library maintainers who need reachability metadata and native test support exercised in real build-tool scenarios.
