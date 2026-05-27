# AR-005-build-documentation-and-release-infrastructure: Repository infrastructure is separate from product runtime behavior

The repository contains internal infrastructure that exists to build, test, document, and release
the product plugins, but is not itself the product surface. This architecture supports the
repository boundaries in §REPO-001-module-boundaries and the fixture goal in
§GOAL-003-repository-fixtures-protect-real-build-scenarios.

## 1. Build logic

`build-logic/` owns internal Gradle convention plugins and repository automation helpers.

### 1.1 Convention plugins

Common build convention plugins own shared Java conventions, publishing conventions,
documentation configuration, functional testing configuration, reachability metadata module
setup, utility module generation, and settings conventions.

### 1.2 Aggregation tasks

Aggregator tasks may coordinate cross-module work such as sample updates, Git staging, commits,
pushes, resets, and publication-oriented repository operations. Aggregation code must remain
internal infrastructure, not product plugin runtime code.

### 1.3 Reachability metadata build support

Reachability metadata fetching and module-generation infrastructure belongs in build logic when
it prepares repository artifacts or test inputs. Product plugins consume the resulting artifacts
through their normal dependencies.

## 2. Documentation

`docs/` owns both user documentation and maintainer-facing specification anchors.

### 2.1 User documentation

The AsciiDoc tree under `docs/src/docs/asciidoc/` remains the source for generated end-user
documentation. It documents how users apply and configure the Gradle and Maven plugins.

### 2.2 Specification documentation

The Markdown tree under `docs/spec/` contains grund declarations, goals, decisions, roadmap
items, functional specs, and architecture specs. It is maintainer-facing and should be updated
before behavior or design changes.

### 2.3 Documentation build

Documentation build logic must keep snippets, generated pages, static assets, and published
documentation output separate from product plugin runtime code.

## 3. Continuous integration

CI workflows are the repository's executable quality gates.

### 3.1 Product plugin tests

CI must have distinct workflows for the Gradle plugin, Maven plugin, JUnit Platform native module,
and GraalVM metadata-oriented tests where those areas have separate runtime costs or prerequisites.
These workflows exercise the supported version matrix in
§REQ-002-supported-build-tool-and-runtime-version-matrix.

### 3.2 Environment preparation

Shared GitHub Actions setup should live in reusable actions or scripts, such as environment
preparation, so workflow differences describe product concerns rather than repeated boilerplate.

### 3.3 Documentation deployment

Documentation deployment workflows should build user documentation from the docs module and
publish generated output without requiring product plugin releases.

### 3.4 Snapshot deployment

Snapshot deployment workflows may publish development artifacts, but release-sensitive secrets and
publication settings must remain in CI/release infrastructure rather than product source code.

## 4. Release and publication

Release infrastructure exists to publish Native Build Tools artifacts and docs while keeping
module ownership clear.

### 4.1 Product artifacts

Gradle and Maven plugin artifacts are the externally visible deliverables. Shared modules may be
published only when they are part of the plugin dependency graph or a documented support artifact.

### 4.2 Version generation

Generated version classes and metadata should be produced by build logic so runtime code can read
stable version values without duplicating release rules.

### 4.3 Changelog and guides

Release-facing documentation, changelog entries, and end-to-end guides should track user-visible
behavior. Maintainer specs under `docs/spec/` explain the behavioral contract behind those docs.

## 5. Infrastructure boundary

Infrastructure code must not become the product plugin API.

### 5.1 Allowed dependencies

Infrastructure may depend on product modules to build, test, package, or publish them. Product
modules should not depend on infrastructure implementation classes except through Gradle build
scripts or generated artifacts intended for runtime use.

### 5.2 Fixture relationship

`samples/`, `test-support/`, plugin test fixtures, and Maven reproducers are evidence for product
behavior and are architecturally owned by §AR-004-samples-and-functional-fixtures, even when build
logic helps execute or publish them.

### 5.3 Spec relationship

Spec files are not generated user documentation. They are stable citation targets for maintainers,
code comments, tests, and future implementation work.
