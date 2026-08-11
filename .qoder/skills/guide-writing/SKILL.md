---
name: guide-writing
description: Create or review structured technical guides for programming languages, frameworks, APIs, tools, and engineering concepts. Use when the user asks for a tutorial, 技术指南, 入门文章, reference guide, learning path, or wants existing technical material reorganized for progressive learning; do not use for product copy, raw API-spec generation, or trivial README edits.
---

# Technical Guide Writing

## Overview

Build technical guides around the reader's decisions and learning dependencies.
The guide's shape follows the subject: causal topics progress from problem to
mechanism; parallel topics provide an independently navigable reference.

Prefer a small, evidence-backed guide over a comprehensive document whose
examples, version claims, or structure cannot be verified.

## Gather the brief

Determine these inputs from the request, conversation, and repository before
asking the user:

- topic and learning goal
- target audience and assumed knowledge
- target versions or version-neutral scope
- boundaries with existing documentation
- desired format, length, renderer, and destination

If missing information would materially change the structure or factual
answer, ask one consolidated question. Otherwise proceed and state the
assumption in the delivery note. When the audience is unknown, default to
developers with general programming experience who are new to this topic.

Use the user's language for prose. Keep identifiers, commands, API names, and
ecosystem terminology in their conventional form.

## Workflow

### 1. Ground the topic

Inspect relevant repository documentation when available. Define the guide's
scope and non-goals, identify the one mental model or decision the reader
should retain, and note facts that may vary by version.

For an audit or review request, report findings and proposed changes without
rewriting files unless the user also asked for modification.

### 2. Select the guide shape

Read [references/guide-types.md](references/guide-types.md), then choose:

- **Progressive** for prerequisites or a causal problem → mechanism → trade-off
  chain.
- **Reference** for parallel categories, commands, APIs, or choices.
- **Hybrid** when a reference guide contains a local sequence that must be
  learned in order.

Do not pause for type or outline approval when the request already fixes the
audience, scope, and outcome. Ask only when competing shapes would materially
change the deliverable.

### 3. Set the evidence gate

Read [references/verification.md](references/verification.md) and assign a
verification level to each example and mutable claim.

When the topic is Java, JVM, Spring, or a closely related framework, also read
[references/language-profiles/java.md](references/language-profiles/java.md).
Do not load that profile for unrelated topics.

### 4. Draft from the matching template

Use:

- [assets/templates/progressive-guide.md](assets/templates/progressive-guide.md)
  for progressive guides.
- [assets/templates/reference-guide.md](assets/templates/reference-guide.md)
  for reference guides and hybrid guides.

Apply [references/content-contract.md](references/content-contract.md) while
drafting. Templates are starting structures, not sections that must always
appear.

### 5. Validate

For a guide saved as Markdown, run:

```text
py -3 scripts/validate_guide.py path/to/guide.md
```

On platforms where `py` is unavailable, use the available Python 3 launcher.
The validator checks headings, table-of-contents anchors, code fences, and
unresolved template placeholders. It does not prove factual or code
correctness; perform the evidence checks selected in step 3 separately.

### 6. Deliver

If the user supplied a destination, write or update that artifact. Otherwise
return Markdown in the conversation and do not create repository files.

Deliver the guide plus a separate verification summary:

```markdown
## Verification summary

- Structure: [validator/checks run]
- Code: [examples compiled, run, statically checked, or not executed]
- Sources: [primary sources used for mutable claims]
- Unverified: [remaining items and why]
```

Never turn a planned check into a completed claim. Missing dependencies,
network access, source access, or toolchains belong under `Unverified`.

## Core content contract

### Sequence by dependency

Introduce a concept before syntax, APIs, or diagrams that depend on it. If a
diagram contains topic-specific terms the reader has not met, define them
immediately before the diagram or annotate the diagram directly.

Progressive sections normally:

1. establish the problem or prerequisite;
2. add one meaningful concept at a time;
3. show the mechanism in use;
4. end with the trade-off or next decision.

Use as many stages as the dependency chain needs; there is no fixed count.

### Keep boundaries without breaking the chain

Avoid repeating material already owned by another guide. Link to that guide
for depth, while retaining the minimum definition, configuration, or trigger
needed to understand and reproduce the current guide.

Use real project context when the user supplied it and it improves relevance.
Remove secrets, personal data, internal hostnames, and unnecessary proprietary
details. Otherwise use a minimal realistic example.

### Add navigation only when it pays for itself

- Add a table of contents when the guide has at least four H2 content sections
  or the user requests one.
- Add a quick-reference aid when at least three parallel operations or choices
  are likely to be revisited.
- Add a diagram when it makes a relationship, hierarchy, state change, or
  multi-step flow materially easier to understand.
- Use Mermaid only when the target renderer supports it; otherwise use compact
  ASCII.

### Classify code honestly

Introduce each code block with one of three evidence states:

- **Illustrative fragment:** teaches one idea and is explicitly not a complete
  program.
- **Complete example, not yet verified:** includes dependencies,
  configuration, complete custom definitions, a run or test command, and the
  expected result, but its dependencies or command have not been verified.
- **Verified runnable example:** meets the complete-example contract, resolves
  its dependencies, and passes the documented run or test command. Record the
  exact command and result.

An unresolved dependency coordinate keeps an example in the complete but
unverified state; it cannot carry the runnable label.

Follow the declared target version. Mention older APIs only when migration or
compatibility is part of the task, and label their status and replacement.

## Quick reference

| Situation | Action |
|---|---|
| Causal or prerequisite chain | Use the progressive template |
| Parallel categories or choices | Use the reference template |
| Parallel overview plus ordered local task | Use a hybrid structure |
| Four or more H2 content sections | Add and validate a table of contents |
| Three or more reusable choices | Consider a compact decision/quick-reference aid |
| Illustrative code | Label it non-runnable; check syntax and symbols |
| Complete, unverified code | Supply prerequisites and command; list unresolved dependencies or execution under `Unverified` |
| Verified runnable code | Resolve dependencies, run the documented command, and record the result |
| Mutable version claim | Declare the target version and use a primary source |
| Java/JVM/Spring topic | Load the Java language profile |

## Common mistakes

- Asking for outline approval after the user has already specified the
  audience, scope, format, and outcome.
- Forcing code, diagrams, a table of contents, or a quick-reference appendix
  into a guide that does not benefit from them.
- Calling an example "runnable" before its dependencies resolve and its
  documented verification command succeeds.
- Linking to another guide while omitting the minimum local context needed to
  follow the current example.
- Treating a remembered API name, default, constant, or architecture detail as
  verified for the declared version.
- Loading domain-specific rules for an unrelated ecosystem.

## Example decision

For a guide comparing Kubernetes probes and then explaining a safe rollout:

1. choose a hybrid shape because the probes are parallel but rollout order is
   causal;
2. declare the Kubernetes target version;
3. source probe behavior from Kubernetes primary documentation;
4. label YAML as runnable only if it is applied or checked with an available
   toolchain;
5. report documentation checks separately from cluster execution.
