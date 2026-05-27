# GRUND-native-build-tools-reason-for-existence: Native Build Tools gives JVM projects build-tool-native workflows for GraalVM Native Image

Native Build Tools exists so projects written in Java or other JVM languages can build, test, and
package GraalVM Native Image applications from the build tools they already use. The repository
provides first-class Gradle and Maven plugins, backed by shared libraries for native-image
invocation, reachability metadata, resource configuration, and native JUnit execution.

The project must keep the Gradle and Maven user experiences aligned where their underlying
semantics are shared, while still respecting each build tool's native extension, task, lifecycle,
and configuration model. This grounding is refined by the repository goals in
§GOAL-build-tool-native-image-workflows and the module ownership model in
§AR-repository-architecture.

## Audiences

- JVM application and library authors who want Native Image support without leaving Gradle or
  Maven.
- Framework and library maintainers who need reachability metadata and native test support to be
  exercised in real build-tool scenarios.
- Native Build Tools maintainers who need a repo layout that separates shared behavior from
  Gradle-specific and Maven-specific integration code.
