---
name: guide-writing
description: Create or review structured technical guides for programming languages, frameworks, APIs, tools, and engineering concepts. Use when the user asks for a tutorial, 技术指南, 入门文章, reference guide, learning path, or wants existing technical material reorganized for progressive learning; do not use for product copy, raw API-spec generation, or trivial README edits.
---

# Technical Guide Writing

## Purpose

Build technical guides around the reader's decisions and learning
dependencies. Prefer a focused, evidence-backed guide over broad coverage that
cannot be verified.

## Gather the brief

Infer these inputs from the request, conversation, and repository before
asking the user:

- topic, learning goal, audience, and assumed knowledge;
- target versions or version-neutral scope;
- boundaries with existing documentation;
- desired format, length, renderer, and destination.

Ask one consolidated question only when missing information would materially
change the structure or factual answer. Otherwise proceed and state the
assumption in the delivery note. When the audience is unknown, default to
developers with general programming experience who are new to the topic.

Use the user's language for prose. Keep identifiers, commands, API names, and
ecosystem terminology in their conventional form.

## Route the task

For every guide creation, substantial reorganization, or guide review, read:

- [references/guide-types.md](references/guide-types.md) to select a
  progressive, reference, or hybrid structure;
- [references/content-contract.md](references/content-contract.md) for
  sequencing, examples, boundaries, navigation, and review criteria.

Load additional resources only when their condition applies:

- Read [references/verification.md](references/verification.md) when the guide
  contains code, commands, mutable implementation facts, security or data
  safety claims, or any claim of verification.
- Read [references/version-freshness.md](references/version-freshness.md) when
  APIs, defaults, lifecycle behavior, deprecation status, package names, or
  recommended patterns may differ across releases.
- Read [references/teaching-patterns.md](references/teaching-patterns.md) for
  beginner-facing tutorials, learning guides, or reviews where readers report
  that concepts, mechanisms, or examples are difficult to understand.
- Read
  [references/language-profiles/java.md](references/language-profiles/java.md)
  only for Java, JVM, Spring, Jakarta, or closely related topics.

For a new or substantially reorganized complete guide, start from the matching
template:

- [assets/templates/progressive-guide.md](assets/templates/progressive-guide.md)
  for progressive guides;
- [assets/templates/reference-guide.md](assets/templates/reference-guide.md)
  for reference and hybrid guides.

Do not load a template for an audit-only review or a small local edit.
Templates are starting structures, not mandatory section lists.

## Work

### Ground the topic

Inspect relevant repository documentation when available. Define the scope and
non-goals, the reader outcome, the organizing insight, and facts that may vary
by version.

For a review request, report findings and proposed changes without rewriting
files unless the user also requested modification.

### Choose and draft

Select the guide shape from knowledge relationships rather than word count.
Do not pause for outline approval when the request already fixes the audience,
scope, and outcome.

Introduce concepts before dependent syntax, APIs, or diagrams. Retain the
minimum local context needed to understand and reproduce the guide while
linking to existing documents for depth.

Follow the evidence state and verification level defined by the routed
references. Never present a planned, unavailable, or failed check as complete.
A cited source is not a checked source: disclose it as unverified unless it
was actually accessed for this guide. Apply the navigation conditions in the
content contract; a guide with four or more content H2 sections needs a table
of contents.
Follow the declared target version and do not present a legacy API as the
current recommendation.

### Validate saved Markdown

When the guide is saved as a Markdown file, run:

```text
py -3 scripts/validate_guide.py path/to/guide.md
```

Use the available Python 3 launcher when `py` is unavailable. This validator
checks document structure only; it does not prove code or factual correctness.
Do not create a repository file solely to validate Markdown returned in the
conversation.

### Deliver

If the user supplied a destination, write or update that artifact. Otherwise
return Markdown in the conversation and do not create repository files.

Deliver the guide plus a separate verification summary:

```markdown
## Verification summary

- Structure: [validator/checks run]
- Code: [compiled, run, statically checked, or not executed]
- Sources: [primary sources used for mutable claims]
- Unverified: [remaining items and why]
```

Missing dependencies, source access, network access, or toolchains belong
under `Unverified`. An empty `Unverified` result is valid only after all
selected evidence gates complete.
