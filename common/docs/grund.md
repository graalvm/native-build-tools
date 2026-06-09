# GRUND-common-purpose: Common libraries keep shared Native Image behavior build-tool neutral

The common libraries exist so Gradle and Maven integrations do not duplicate Native Image utility,
metadata, tracing-agent, resource, schema, and native-test runtime behavior. They are the shared
library realization of [§root/GRUND-why-nbt](../../docs/spec/grund.md#grund-why-nbt-native-build-tools-gives-jvm-projects-build-tool-native-workflows-for-graalvm-native-image) and the parity contract
in [§root/FS-plugin-common](../../docs/spec/functional/plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior).

This subproject owns build-tool-neutral behavior in [§FS-common-libraries](functional-spec.md#fs-common-libraries-common-libraries-provide-shared-native-image-utilities-and-metadata-workflows) and implementation
boundaries in [§AR-common-libraries](architecture.md#ar-common-libraries-shared-libraries-stay-independent-from-gradle-and-maven-apis). Product plugins adapt common behavior through their own APIs;
common code should remain independent from Gradle and Maven plugin implementation classes.
