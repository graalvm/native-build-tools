# DEC-001-adopt-component-functional-architecture-docs: Adopt component functional-spec and architecture docs beside existing user documentation

The repository uses component-specific documentation folders while preserving the functional versus
architecture distinction through file names and ID kinds. Substantial components own
`functional-spec.md` for behavior contracts and `architecture.md` for implementation structure and
ownership boundaries. IDs use `FS` for functional behavior and `AR` for architecture, with the
component name in the slug and path.

The existing AsciiDoc user documentation remains in `docs/src/docs/asciidoc/`; the Markdown files
under `docs/spec/` are maintainer-facing specification anchors. Follow-up coverage work is tracked
by §RM-001-expand-spec-coverage-from-module-boundaries-to-feature-details and
§RM-002-connect-code-and-tests-to-grund-citations.

## 1. Reference format

The specification follows the referenced grund adoption's pattern of durable declarations,
explicit citation chains, and detailed sections under a small set of component documents. Native
Build Tools keeps its initial numbered ID format from §REPO-001-module-boundaries while adopting
the reference PR's feature-level section style.

## 2. Why component docs

Native Build Tools has two product plugins with overlapping Native Image behavior and different
build-tool integration models. Component docs let maintainers describe shared behavior once in
`common/` and then describe Gradle or Maven adaptation in the product component docs.

## 3. Consequences

Behavior changes should update the relevant functional spec before code. Placement, dependency,
or ownership changes should update the relevant architecture spec before code. User-facing guide
changes still belong in the AsciiDoc documentation, but they can cite or be checked against the
maintainer-facing spec.
