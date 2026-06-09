# GRUND-common-purpose: Common libraries keep shared Native Image behavior build-tool neutral

The common libraries exist so Gradle and Maven integrations do not duplicate Native Image utility,
metadata, tracing-agent, resource, schema, and native-test runtime behavior. They are the shared
library realization of §root/GRUND-product-purpose and the parity contract
in §root/FS-plugin-common.

This subproject owns build-tool-neutral behavior in §FS-common-libraries and implementation
boundaries in §AR-common-libraries. Product plugins adapt common behavior through their own APIs;
common code should remain independent from Gradle and Maven plugin implementation classes.
