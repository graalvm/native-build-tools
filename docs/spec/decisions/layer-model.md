# DEC-layer-model: Native Image layers use shared selection and build-tool-native wiring

Native Image layer selection is represented once in `common`, while Gradle and Maven own their
native task and artifact graphs. This implements [§FS-native-builds.6](../functional/native-image-builds.md#6-layered-images),
[§common/FS-common-libraries.1](../../../common/docs/functional-spec.md#1-shared-native-image-utilities),
[§gradle/FS-plugin-model.2](../../../native-gradle-plugin/docs/functional/plugin-model.md#2-extension-surface), and
[§maven/FS-goal-surface.6](../../../native-maven-plugin/docs/functional/goal-surface.md#6-layer-creation).

## 1. Decision

Gradle exposes named layers outside the binary container and binaries consume typed layer objects.
Maven exposes a `layer-create` goal that attaches a `nil` artifact and compile goals resolve
declared `nil` dependencies. Both adapters resolve dependency models to paths before using the
shared immutable artifact selection and renderer.

## 2. Compatibility

The experimental Gradle `createLayer` and `useLayer(String)` calls remain as deprecated adapters.
The legacy `lib` naming rule applies only to those declarations. Maven layer configuration is
opt-in and does not alter existing builds.
[§REQ-backwards-compatibility](../requirements.md#req-backwards-compatibility-plugin-upgrades-keep-existing-gradle-and-maven-builds-working).

## 3. Boundary

The shared model does not contain Gradle, Maven, Aether, task, project, configuration, or artifact
types. The plugins do not duplicate dependency resolution: they use Gradle artifact views and
Maven's project/repository model respectively.
[§NGOAL-no-buildtool-duplicates](../non-goals.md#ngoal-no-buildtool-duplicates-the-plugins-do-not-reimplement-capabilities-that-gradle-or-maven-already-provide).

## 4. GraalVM release support

Native Build Tools supports and tests Native Image layer consumption on GraalVM 25.1 and later.
Layer consumption on GraalVM 25.0.x remains permitted for backwards compatibility, but it is
unsupported and proceeds at the user's own risk because Native Image may fail internally while
loading an otherwise valid layer. Consumers on 25.0.x receive a warning rather than a build-tool
failure. Layer creation alone is not subject to this consumption warning. Native Image layers
remain an experimental upstream feature. This policy specializes
[§FS-native-builds.6](../functional/native-image-builds.md#6-layered-images),
[§REQ-support-matrix.1](../requirements.md#1-declared-support), and
[§REQ-backwards-compatibility.1](../requirements.md#1-deprecation-over-removal) without claiming
that the upstream feature is generally stable.
