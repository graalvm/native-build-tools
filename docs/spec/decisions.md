# DEC-adopt-component-functional-architecture-docs: Adopt component spec docs beside existing user documentation

The repository uses component-specific Markdown files with component-specific ID kinds. The
top-level `FS` and `AR` declarations describe the repository as a whole; substantial components
use `GRADLE`, `MAVEN`, `COMMON`, `TESTING`, `BUILD`, `CI`, and `E2E` IDs because those files
combine behavior contracts, implementation structure, and verification boundaries.

The existing AsciiDoc user documentation remains in `docs/src/docs/asciidoc/`; the Markdown files
under `docs/spec/` are maintainer-facing specification anchors. Follow-up coverage work is tracked
by §RM-expand-spec-coverage-from-module-boundaries-to-feature-details and
§RM-connect-code-and-tests-to-grund-citations.

## 1. Reference format

The specification follows the referenced grund adoption's pattern of durable declarations,
explicit citation chains, and detailed sections under a small set of component documents. Native
Build Tools uses slug-only IDs to keep declaration names readable while adopting the reference
PR's feature-level section style.

## 2. Why component docs

Native Build Tools has two product plugins with overlapping Native Image behavior and different
build-tool integration models. Component docs let maintainers describe shared behavior once in
`common.md` and then describe Gradle or Maven adaptation in the product component docs.

## 3. Consequences

Behavior changes should update the relevant component spec before code. Placement, dependency,
workflow, or ownership changes should update the relevant architecture, CI, E2E, or component
section before code. User-facing guide changes still belong in the AsciiDoc documentation, but
they can cite or be checked against the maintainer-facing spec.
