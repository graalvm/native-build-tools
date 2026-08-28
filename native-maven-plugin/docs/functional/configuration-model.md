# FS-config-model: Maven XML and command-line properties configure Native Image builds

The plugin maps Maven XML configuration and system properties into one native image option model.
Users should be able to keep stable build settings in the POM and pass short-lived overrides with
`-D...` properties.

## 1. Native Image options

The plugin must support image name, main class, build args, runtime args, debug, verbose, fallback,
shared-library output, quick build, argument-file usage, classpath, classes directory, dependency
exclusions, environment variables, system properties, JVM args, configuration file directories,
metadata repository settings, required Native Image version, and agent configuration.

The fallback parameter is deprecated because Native Image removed fallback support in GraalVM
25.1. It remains available for compatibility: setting it to `false` produces `--no-fallback`
before 25.1 and when the GraalVM release cannot be identified, while 25.1 and later omit the
generated flag. Explicit user build arguments remain unchanged.

## 2. Command-line properties

Configuration values documented as Maven command-line properties must be overridable through
`-D...` properties for temporary runs. The property path must feed the same option state as XML
configuration so behavior does not diverge by configuration source.

```bash
mvn -Pnative -DquickBuild -Dverbose -DskipTests package
```

## 3. Parent POM merging

Configuration that Maven natively supports as mergeable, such as `<buildArgs>`, must preserve
Maven's parent/child merge behavior. Child projects must be able to append to parent build
arguments when they use Maven's `combine.children="append"` convention.

## 4. Toolchain and executable lookup

The plugin must locate a Native Image executable using Maven toolchains when appropriate and
environment/path fallbacks otherwise. When toolchain enforcement is enabled, failing to find a
toolchain-provided Native Image executable must fail clearly.

## 5. Override precedence

Maven's standard parameter binding decides precedence between configuration sources. When a
parameter is set in `<configuration>` XML, that explicit value takes precedence; the matching
command-line property from [§FS-config-model.2](configuration-model.md#2-command-line-properties), such as `-DskipNativeBuild=...`, applies only
when no explicit configuration is present. The exception is a parameter intentionally modeled to
let the property win for one run, such as the agent toggle in [§FS-tracing-agent.1](tracing-agent.md#1-agent-enablement) where
`-Dagent=false` disables an agent enabled in the POM. This is the Maven adaptation of
[§root/FS-option-precedence](../../../docs/spec/functional/option-precedence.md#fs-option-precedence-command-line-input-and-durable-configuration-produce-one-option-state).

## 6. Plugin-wide goal skipping

Setting `<skip>true</skip>` in the native Maven plugin's root `<configuration>` must skip every
plugin goal before it resolves dependencies, downloads metadata, generates files, or otherwise
causes a goal-specific side effect. When it is absent or false, each goal retains its existing
specialized skip controls and behavior.

## 7. Layer configuration

A `layer-create` execution accepts one layer definition with a required name and neutral contents:
`all`, module names, package names, explicit files, and selected `groupId:artifactId[:version]`
dependencies with configurable transitivity. The shared Maven plugin descriptor marks the layer
parameter optional so unrelated goals and IDE plugin validation do not require `<layer>`; an
explicit `layer-create` execution without a configured, non-blank layer name still fails.
`includeDependencies`, when present, accepts only `all`; other values fail configuration instead
of being ignored. Layer names accept only letters, digits, dots, underscores, and hyphens.
Version-qualified selections must match the resolved root dependency exactly before including its
transitive dependency trail. Normal compile goals accept `useLayers` entries that select project
dependencies of type `nil` by coordinates. Missing, ambiguous, and duplicate selections fail
before Native Image is invoked. Loading the plugin with `<extensions>true</extensions>` registers
`nil` as a Maven artifact type early and is recommended for consistent reactor and repository
resolution, although Maven builds that already preserve the explicit `nil` type may resolve
without it.
[§root/REQ-backwards-compatibility.2](../../../docs/spec/requirements.md#2-configuration-compatibility).

## 8. Preserve dependency selection

Application compile configuration accepts `<preserve><dependencies>...</dependencies></preserve>`.
Each entry selects `groupId:artifactId[:version]` and includes its resolved transitive dependency
trail by default; `<transitive>false</transitive>` limits it to the matched root. Selection uses the
resolved Maven project graph, preserves stable first-seen path order, and accepts reactor class
outputs when a packaged artifact file is not yet available. Blank, malformed, missing, ambiguous,
empty, and fileless selections fail as normal Maven execution errors before Native Image starts.

The parameter belongs to the `compile-no-fork` mojo hierarchy, so it applies to `compile`,
`compile-no-fork`, the deprecated `build` alias, and `write-args-file`. It is not exposed by
`native:test`, `native:integration-test`, or `layer-create`. The first-class XML surface contains
dependencies only; raw `all`, module, package, and explicit-path Preserve selectors remain `<buildArgs>` under
[§root/FS-native-builds.7](../../../docs/spec/functional/native-image-builds.md#7-dependency-preservation).
[§REQ-maven-model](../requirements.md#req-maven-model-the-maven-plugin-preserves-maven-model-compatibility).
