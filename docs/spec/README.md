# Native Build Tools Specification

This is the maintainer-facing specification. Top-level `FS` and `AR` files describe the whole
repository, while each substantial component owns one Markdown file with its own kind because
those files combine functional behavior, architecture, and verification notes. The ID kind gives
the component or artifact type.
Declarations use numbered subsections as feature-level citation targets, so implementation and
test comments can cite narrow behavior such as
`§GRADLE-plugin.5.3`.

The canonical registry of ID kinds and where each kind lives is the project map in
[AGENTS.md](../../AGENTS.md); [`.agents/grund.toml`](../../.agents/grund.toml) is the
machine-readable source. Run `grund list` to enumerate every ID and `grund <ID>` to resolve a
citation rather than maintaining a second copy of the registry here.

## Component documents

| File | Scope |
| --- | --- |
| [functional-spec.md](functional-spec.md) | Top-level functional surface |
| [architecture.md](architecture.md) | Top-level architecture outlook |
| [gradle-plugin.md](gradle-plugin.md) | Gradle plugin behavior and architecture |
| [maven-plugin.md](maven-plugin.md) | Maven plugin behavior and architecture |
| [common.md](common.md) | Shared metadata, resource, and common-library behavior |
| [testing.md](testing.md) | Native test behavior, samples, and fixtures |
| [build-infra.md](build-infra.md) | Build, docs, release, and CI behavior and architecture |
| [ci.md](ci.md) | Pull request CI workflows and shared actions |
| [e2e.md](e2e.md) | End-to-end and functional test execution |

Single-file kinds — motivation (`grund.md`), goals (`goals.md`), non-goals (`non-goals.md`),
requirements (`requirements.md`), decisions (`decisions.md`), roadmap (`roadmap.md`), and glossary
(`glossary.md`) — live beside the component files; see the project map above for the authoritative
kind list.

## Citation shape

Functional specs describe user-visible or build-visible behavior. Architecture specs describe
ownership, dependency direction, and implementation structure. Prefer the deepest applicable
section citation when writing code comments, tests, or future docs.
