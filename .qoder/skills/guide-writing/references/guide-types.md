# Guide type selection

Choose the structure from the topic's knowledge relationships, not from the
number of facts or the requested word count.

## Decision table

| Observable shape | Guide type | Reader experience |
|---|---|---|
| Prerequisites, cause and effect, or problem → mechanism → trade-off | Progressive | Reads in order and builds one mental model |
| Parallel categories, commands, APIs, or alternatives | Reference | Jumps from an overview to any independent section |
| Parallel choices followed by an ordered workflow or migration | Hybrid | Uses reference navigation, then follows local sequences |

## Progressive guides

Use a progressive structure when a later section would be confusing or
misleading without an earlier concept.

Build the sequence from dependencies:

1. Name the reader's problem or missing prerequisite.
2. Establish the minimum mental model.
3. Introduce one consequential mechanism at a time.
4. Apply the mechanism to a realistic example.
5. Close with limits, trade-offs, or the next decision.

The number of stages follows the dependency chain. Two strong stages are
better than three artificial ones; five may be appropriate when each stage
unlocks the next.

Good fits include authentication flows, compiler pipelines, framework
lifecycle mechanisms, migrations, debugging methods, and tool workflows.

## Reference guides

Use a reference structure when the main concepts are siblings rather than
steps. Begin with a landscape or selection model, then make each branch
independently readable.

Each branch should answer the same comparison dimensions when relevant:

- what it is and what decision it supports;
- when it fits and when it does not;
- one representative example;
- important constraints or variants;
- where to go for deeper detail.

Good fits include language feature families, API groups, command catalogs,
data structures, error taxonomies, and competing architecture choices.

Do not turn the overview into a dense manual. It is a navigation model; the
branch sections carry the detail.

## Hybrid guides

Choose reference as the outer structure and use progressive ordering only
inside the branch that has real dependencies.

Example: a Kubernetes probes guide compares readiness, liveness, and startup
probes as parallel concepts, then teaches rollout configuration in order:
startup safety → readiness gating → liveness recovery.

Hybrid is not permission to duplicate every concept in two structures. The
overview locates the concepts; the ordered section teaches their interaction.

## When to ask the user

Proceed without confirmation when the audience, outcome, scope, and requested
format make one structure clearly suitable.

Ask one consolidated question when either choice would produce a materially
different artifact, for example:

- “introduction” could mean a learning tutorial or an API lookup page;
- the target reader could be a non-technical decision maker or an implementer;
- a migration guide lacks both source and target versions;
- existing documents create an unresolved ownership boundary.

When clarification is helpful but not essential, choose the strongest fit,
state the assumption in the delivery note, and continue.

## Selection examples

| Topic and goal | Selection | Reason |
|---|---|---|
| OAuth Authorization Code with PKCE for new frontend engineers | Progressive | Security motivation and protocol steps depend on prior concepts |
| Redis data structures for experienced backend developers | Reference | The types are parallel choices |
| Kubernetes probes plus safe rollout order | Hybrid | Parallel comparison plus ordered configuration |
| CAP theorem for product managers | Progressive, no code | The mental model is causal; implementation syntax is irrelevant |
| Spring Security 6 → 7 migration | Progressive with reference aids | Migration order matters; current/legacy mappings are parallel |
