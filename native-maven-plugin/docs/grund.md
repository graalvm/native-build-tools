# GRUND-plugin-purpose: The Maven plugin realizes Native Build Tools through Maven idioms

The Maven plugin exists so Maven users can build, test, and configure GraalVM Native Image
artifacts through Maven goals, lifecycle bindings, XML configuration, system properties,
dependency scopes, and toolchains. It is the Maven subproject realization of
§root/GRUND-product-purpose and the shared plugin contract in
§root/FS-plugin-common.

This subproject owns the Maven-facing behavior in the focused specs under `docs/functional/`
and the implementation boundaries in §AR-maven-plugin. Repository-wide non-goals still apply here, especially
§root/NGOAL-no-flag-mirroring and
§root/NGOAL-no-buildtool-duplicates.
