# GOAL-common-libraries-shared-native-image-semantics: Shared Native Image semantics live once in common libraries

Common libraries centralize build-tool-neutral Native Image behavior: argument handling, resource
analysis, tracing-agent modes, metadata repository lookup, missing-metadata reporting, schema
validation, and native-test runtime support. This goal realizes §root/GOAL-plugin-parity through
§GRUND-common-libraries-purpose and is specified by §FS-common-libraries.

# GOAL-common-libraries-runtime-support-is-reusable: Native-test and metadata support remains reusable by product plugins

Runtime support such as the JUnit native launcher, Native Image feature registration, and metadata
repository utilities stays available to both product plugins and their tests without copied logic
in plugin modules. This goal supports §root/FS-native-tests and the common behavior in
§FS-common-libraries.
