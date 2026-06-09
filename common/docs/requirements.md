# REQ-no-buildtool-apis: Common runtime libraries do not depend on Gradle or Maven APIs

Common modules must not depend on Gradle API classes, Maven plugin API classes, product plugin
implementation classes, or plugin test fixtures. Product plugins are responsible for translating
task, goal, project, scope, and lifecycle state into common-library inputs. This requirement
constrains §AR-common-libraries.5 and supports §GOAL-shared-native-image.

# REQ-stable-semantics: Shared behavior changes preserve plugin parity

Changes to common argument handling, resource analysis, tracing-agent modes, reachability metadata
lookup, missing-metadata reporting, schema validation, or native-test runtime behavior must remain
compatible with both product plugins unless a plugin-specific spec explicitly narrows the behavior.
This requirement constrains §FS-common-libraries and follows §GOAL-shared-native-image.

# REQ-version-schema-compat: Common metadata and schema behavior tracks supported Native Image versions

Common metadata and schema utilities must choose version-specific behavior from declared Native
Image or metadata repository version information and fail early when a schema-backed metadata file
is invalid. This requirement constrains §FS-common-libraries.5 and §FS-common-libraries.7 and
supports §root/REQ-support-matrix.
