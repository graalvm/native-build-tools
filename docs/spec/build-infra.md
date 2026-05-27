# BUILD-infrastructure: Build, documentation, and release infrastructure

Repository infrastructure exists to build, test, document, publish, and validate Native Build
Tools without becoming part of the product runtime API. It supports the repository architecture in
§AR-repository-architecture, the end-to-end test contract in
§E2E-functional-test-suite, and the pull request gates in §CI-pull-request-ci.

## 1. Build orchestration

The root Gradle build coordinates product modules, shared modules, samples, documentation, and
internal convention plugins. Root and build-logic tasks may assemble, test, publish locally, and
prepare generated artifacts for the repository's modules. Aggregation tasks may coordinate
cross-module maintenance work, but product behavior must still live in product or common modules.

## 2. Build logic

`build-logic/` owns internal Gradle convention plugins and repository automation helpers. Common
build convention plugins own shared Java conventions, publishing conventions, documentation
configuration, functional testing configuration, reachability metadata module setup, utility
module generation, and settings conventions.

Reachability metadata fetching and module-generation infrastructure belongs in build logic when
it prepares repository artifacts or test inputs. Product plugins consume the resulting artifacts
through their normal dependencies.

## 3. Documentation

The AsciiDoc tree under `docs/src/docs/asciidoc/` remains the source for generated end-user
documentation. It documents how users apply and configure the Gradle and Maven plugins. The
Markdown tree under `docs/spec/` contains grund declarations, goals, decisions, roadmap items,
component specs, CI specs, and architecture specs. It is maintainer-facing and should be updated
before behavior or design changes.

Documentation build logic must keep snippets, generated pages, static assets, and published
documentation output separate from product plugin runtime code.

## 4. Continuous integration

CI workflows are the repository's executable quality gates. The PR workflows are specified in
§CI-pull-request-ci and cover Gradle plugin behavior, Maven plugin behavior, shared common
libraries, JUnit native support, reachability metadata behavior, end-to-end functional tests, and
spec citations.

Shared GitHub Actions setup should live in reusable actions or scripts, such as environment
preparation, so workflow differences describe product concerns rather than repeated boilerplate.

## 5. Release and publication

Release infrastructure publishes Native Build Tools artifacts and documentation while keeping
module ownership clear. Gradle and Maven plugin artifacts are the externally visible deliverables.
Shared modules may be published only when they are part of the plugin dependency graph or a
documented support artifact.

Generated version classes and metadata should be produced by build logic so runtime code can read
stable version values without duplicating release rules. Snapshot deployment workflows may publish
development artifacts, but release-sensitive secrets and publication settings must remain in CI or
release infrastructure rather than product source code.

## 6. Infrastructure boundary

Infrastructure may depend on product modules to build, test, package, or publish them. Product
modules should not depend on infrastructure implementation classes except through Gradle build
scripts or generated artifacts intended for runtime use.

`samples/`, `test-support/`, plugin test fixtures, and Maven reproducers are evidence for product
behavior and are specified by §TESTING-native-tests-and-fixtures. Spec files are not generated
user documentation; they are stable citation targets for maintainers, code comments, tests, CI
workflows, and future implementation work.
