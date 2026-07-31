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
dependencies with configurable transitivity. Version-qualified selections must match the resolved
root dependency exactly before including its transitive dependency trail. Normal compile goals
accept `useLayers` entries that select project dependencies of type `nil` by coordinates. Missing,
ambiguous, and duplicate selections fail before Native Image is invoked. The plugin must be loaded
with `<extensions>true</extensions>` so Maven registers `nil` as a resolvable artifact type.
[§root/REQ-backwards-compatibility.2](../../../docs/spec/requirements.md#2-configuration-compatibility).
