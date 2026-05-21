# AR-001-gradle-plugin-boundary: The Gradle plugin module adapts shared behavior to Gradle APIs

`native-gradle-plugin` owns Gradle plugin registration, extension objects, task types, task actions,
command-line providers, Gradle services, artifact transforms, and Gradle functional test
infrastructure.

The module may depend on shared common libraries, but shared common libraries should not depend on
Gradle implementation classes. Gradle-specific behavior should remain in this module unless it is
actually build-tool-neutral and can be expressed without Gradle APIs.

This architecture supports §FS-001-gradle-plugin-native-image-workflow and the shared-library
direction in §AR-003-shared-common-libraries.
