# Teaching patterns for reader understanding

Use these patterns for beginner-facing tutorials, learning guides, and
clarity-focused reviews. Apply them to consequential concepts; do not turn
each pattern into a mandatory section or expand material the reader already
understands.

## Close the explanation loop

Explain a core concept with the smallest useful combination of:

1. a precise definition that answers what the thing is;
2. a familiar comparison that reduces cognitive load;
3. the problem it exists to solve;
4. its observable behavior in the guide's current scenario.

An analogy is a bridge, not a definition. Do not explain an abstract term by
replacing it with an equally abstract synonym. Name the important difference
between the analogy and the real mechanism when the analogy could mislead.

Introduce a system role before the first sentence, diagram, or code path that
depends on it. In a Chinese guide, prefer `中文（English）` at first use when a
stable Chinese term exists, then use the clearest term consistently. Preserve
official names where translation would make source lookup or API use harder.

State the correct behavior directly. Add the common misconception afterward
only when it helps the reader avoid a likely mistake; avoid leading with a
double negative.

## Explain mechanisms as behavior

For a mechanism that transforms, routes, schedules, acknowledges, or creates
something, establish:

- the initial state and prerequisites;
- the input and who supplies it;
- the consequential processing steps;
- the output or observable result.

Use a compact input-to-output map, sequence, or three-to-five-step walkthrough
when a one-line responsibility leaves the transformation hidden. The number
of steps follows the mechanism; it is not a template quota.

Explain quantitative settings with numbers. Show what happens before and
after the limit is reached, which event frees capacity, and what proceeds
next. This is more useful than restating the configuration key in prose.

## Distinguish neighboring concepts

When names, shapes, or lifecycle positions are easy to confuse, state the
distinction explicitly: “X is ..., whereas Y is ...”. Compare responsibility,
lifetime, ownership, and timing as relevant. Use a small table or sequence
only when it makes those differences faster to retrieve.

For enum values or modes, compare their behavior using the same initial state
and input. Emphasize the event that changes state, such as whether completion
occurs on arrival, successful return, explicit acknowledgement, or timeout.

Ground general concepts in the topic. Give a positive and negative example
when useful, then connect the distinction to the decision the reader is
making. An isolated dictionary definition is not enough when the term controls
implementation behavior.

## Pair concepts with the right evidence

For a code-facing API, annotation, option, or object, follow the explanation
with the smallest fragment that exposes its distinctive use. Explain each new
topic-specific symbol in the block. A three-to-ten-line fragment is often
enough, but clarity and completeness matter more than a fixed line count.

For a non-code concept, use a concrete scenario, positive/negative example, or
small diagram instead of forcing code. Mark demonstration helpers and
framework hooks as optional when they are not required by the production
pattern. Readers should be able to separate the mechanism being taught from
the scaffolding used to trigger it.

## Choose a teaching narrative

For causal or flow-oriented topics, one representative item can travel
through the complete mechanism before the guide summarizes the concepts. Make
the item's initial state explicit, and identify where each name or routing
decision shown in a diagram originated.

For parallel concepts, reuse one scenario or input across branches so the
behavioral differences remain visible. Give each branch enough independent
explanation; a summary table closes the comparison but does not replace it.

Use these narratives only when they clarify the reader's path. A concise
expert reference may need direct definitions and decision dimensions instead.
