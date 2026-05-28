# AR-build-infrastructure: Build infrastructure stays outside product runtime behavior

Build infrastructure owns repository automation, generated support artifacts, documentation
publication, validation wiring, and release support. It may assemble, test, and publish product
modules, but product runtime code must not depend on infrastructure implementation classes.
Infrastructure behavior is specified by §FS-build-infrastructure.

## 1. Build logic ownership

`build-logic/` owns internal Gradle convention plugins and helper code for repository builds. It
may configure Java conventions, publication, documentation, functional testing, reachability
metadata module setup, generated version classes, and settings conventions.

Generated artifacts that product modules use at runtime must have explicit generation tasks and
stable inputs. Product modules consume generated outputs, not the build-logic implementation
classes that produce them.

## 2. Documentation architecture

The AsciiDoc tree under `docs/src/docs/asciidoc/` is the source for generated user documentation.
The Markdown tree under `docs/spec/` is the root maintainer-facing specification and citation
graph. The Gradle and Maven plugin modules also own standalone grund specs under their local
`docs/` directories. Documentation build logic may copy snippets, render pages, and publish static
assets, but it must not make generated documentation a source of product runtime behavior.

## 3. CI and release boundaries

GitHub workflows under `.github/workflows/` and reusable actions under `.github/actions/` own
remote validation, dev-build checks, snapshot deployment, and release-sensitive publication
steps. Workflow behavior is specified by §CI-pull-request-ci, and local execution equivalents are
specified by §E2E-gradle-plugin-functional-tests and §E2E-maven-plugin-functional-tests.

Secrets, release credentials, and publication destinations belong to CI or release
configuration. Product modules expose publishable artifacts; infrastructure decides when and how
those artifacts are published.

## 4. Fixture and sample boundary

`samples/`, `test-support/`, plugin test fixtures, and Maven reproducers are evidence for product
behavior. They may depend on product artifacts under test and support artifacts published to a
local test repository, but they should not become general runtime libraries for product code.
