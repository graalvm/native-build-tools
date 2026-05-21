# AR-002-maven-plugin-boundary: The Maven plugin module adapts shared behavior to Maven APIs

`native-maven-plugin` owns Maven mojos, Maven plugin descriptor generation, Plexus integration,
Maven configuration objects, Maven repository seeding for functional tests, Maven-specific SBOM
integration, and issue reproducers.

The module may depend on shared common libraries, but shared common libraries should not depend on
Maven implementation classes. Maven-specific lifecycle, repository, descriptor, and configuration
concerns should remain in this module unless the behavior can be expressed as build-tool-neutral
Native Image support.

This architecture supports §FS-002-maven-plugin-native-image-workflow and the shared-library
direction in §AR-003-shared-common-libraries.
