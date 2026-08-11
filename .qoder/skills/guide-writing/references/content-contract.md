# Content design contract

Use this contract while drafting or reviewing a technical guide. Apply the
conditions that match the task; do not add sections solely because they appear
in a template.

## Reader contract

Open with enough context for the intended reader to answer:

- What problem or decision is this guide about?
- What knowledge does it assume?
- What will the reader be able to understand or do afterward?
- What is deliberately outside scope?

Prefer concrete reader outcomes over broad promises such as “master” or
“complete coverage.”

## One organizing insight

State the guide's organizing insight in one sentence before drafting. Every
top-level section should either establish, apply, compare, or qualify it.

If an example could be demonstrated more naturally by a different technique,
replace the example. The first example should show the topic's distinctive
value rather than a task another tool solves better.

## Dependency order

Build a prerequisite graph mentally before writing:

1. Define terms the reader needs for the first decision.
2. Explain the relationship or mechanism.
3. Introduce syntax and APIs that express that mechanism.
4. Show interactions, edge cases, and trade-offs.

A forward reference such as “explained later” is a signal to check the order.
Short previews are acceptable only when the reader can already understand the
current section without the deferred concept.

Before a diagram, define or annotate every topic-specific term that is new to
the reader. A diagram should consolidate understanding, not become a glossary
the reader must decode.

## Sections and transitions

Give each section one clear responsibility. For progressive guides, connect it
to the dependency established by the previous section. For reference guides,
repeat the same comparison dimensions so branches remain independently
scannable.

Create a dedicated section when a topic owns a substantial decision or is
otherwise scattered across three or more sections. Keep a small variant local
when extracting it would fragment the reading path.

Allocate space according to importance and expected use, not API count. A
frequent decision deserves more explanation than a rare option even when the
rare option has more parameters.

## Boundaries with existing documents

Inspect related documentation before drafting when it is available.

- Link to the existing owner for complete definitions, operational runbooks,
  or framework-specific details.
- Retain the minimum local definition or trigger needed to follow the current
  guide.
- Preserve every node needed to reproduce a promised behavior. Omitting a
  deeper explanation is fine; omitting the event that triggers the behavior is
  not.
- State scope and non-goals when nearby documents could otherwise overlap.

## Examples

Use examples that are realistic for the target reader and small enough to
verify.

### Illustrative fragments

Introduce the block with `Illustrative fragment` or the equivalent in the
guide's language. State what it omits when readers might mistake it for a
complete program. Check syntax, symbol consistency, and the behavior being
illustrated; do not claim execution.

### Complete examples not yet verified

Introduce the block with `Complete example, not yet verified` or the
equivalent. Include:

- dependency and version information;
- required configuration;
- imports and complete custom definitions;
- a run or test command;
- the expected observable result.

List unresolved dependency coordinates and the unexecuted or failed command
under `Unverified`. This state is complete enough for a reader to test, but it
does not carry the runnable label.

### Verified runnable examples

Introduce the block with `Verified runnable example` or the equivalent only
after its dependencies resolve and the documented run or test command
succeeds. Record the exact command, exit status, and relevant observable
result. A plausible dependency coordinate or inspection-only review does not
promote an example to this state.

When a real project example is useful, remove secrets, personal data, private
hosts, and irrelevant proprietary details. Keep ecosystem conventions and
identifiers recognizable.

## Version and terminology

Declare a target version when APIs, behavior, defaults, class names, or
architecture change across releases. Use the target version's supported
current API. Mention older names only for migration or compatibility, marking
their status and replacement.

Define external terms using:

1. a precise definition;
2. a familiar comparison when it reduces cognitive load;
3. a production/development distinction when operational behavior differs.

Use analogies as bridges, not definitions. Remove an analogy that introduces
another unfamiliar domain or hides an important difference.

## Navigation and visual aids

Add a table of contents when there are at least four H2 content sections or
the user requests one. Ensure visible labels match their destination headings
and validate the anchors.

Add a quick-reference aid when at least three parallel operations or choices
will be revisited. It should encode a decision, trigger, command, or constraint
rather than repeat prose.

Add a diagram only for a relationship, hierarchy, state transition, or
multi-step flow that is materially harder to understand linearly. Use Mermaid
only when the renderer supports it; otherwise prefer small ASCII diagrams.

## Review checklist

Before delivery, check:

- Audience, outcome, scope, and assumptions are explicit.
- Concept dependencies precede syntax and diagrams.
- Headings express the guide's actual decisions, not a generic template.
- Examples are classified honestly and define every required custom symbol.
- Promised outcomes have visible trigger paths.
- Mutable claims follow the selected evidence gate.
- Existing-document boundaries preserve minimum local context.
- Tables and diagrams earn their space.
- The guide contains no unresolved placeholders or sensitive data.
