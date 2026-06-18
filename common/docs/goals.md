# GOAL-shared-native-image: Shared Native Image semantics live once in common libraries

Common libraries centralize build-tool-neutral Native Image behavior: argument handling, resource
analysis, tracing-agent modes, metadata repository lookup, missing-metadata reporting, schema
validation, and native-test runtime support. This goal realizes [§root/GOAL-plugin-parity](../../docs/spec/goals.md#goal-plugin-parity-shared-native-image-behavior-remains-consistent-across-gradle-and-maven) through
[§GRUND-common-purpose](grund.md#grund-common-purpose-common-libraries-keep-shared-native-image-behavior-build-tool-neutral) and is specified by [§FS-common-libraries](functional-spec.md#fs-common-libraries-common-libraries-provide-shared-native-image-utilities-and-metadata-workflows).

# GOAL-reusable-runtime: Native-test and metadata support remains reusable by product plugins

Runtime support such as the JUnit native launcher, Native Image feature registration, and metadata
repository utilities stays available to both product plugins and their tests without copied logic
in plugin modules. This goal supports [§root/FS-native-tests](../../docs/spec/functional/native-tests.md#fs-native-tests-both-plugins-compile-and-execute-junit-tests-as-a-native-image) and the common behavior in
[§FS-common-libraries](functional-spec.md#fs-common-libraries-common-libraries-provide-shared-native-image-utilities-and-metadata-workflows).
