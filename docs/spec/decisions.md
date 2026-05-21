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
