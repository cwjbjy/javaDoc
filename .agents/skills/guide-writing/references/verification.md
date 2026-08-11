# Risk-based verification

Verification depth follows the cost of being wrong. Select the level per
example or claim rather than assigning one level to the whole guide.

## Verification levels

| Content | Required evidence |
|---|---|
| Conceptual explanation or illustrative fragment | Internal consistency, syntax review, and symbol/flow checks |
| Complete example, not yet verified | Syntax and symbol review; list unresolved dependencies and the unexecuted or failed command |
| Verified runnable standalone example | Resolve dependencies, compile or execute in an available toolchain, and record the command/result |
| Framework integration or configuration | Dependency resolution plus the smallest meaningful smoke test |
| Version-sensitive API, default, lifecycle, or deprecation claim | Primary documentation, specification, release notes, or source for the declared version |
| Internal constant, class name, package path, ordering, or architecture detail | Version-matched source or authoritative generated reference; smoke-test when practical |
| Security- or data-safety-sensitive procedure | Primary guidance plus explicit prerequisites, failure modes, and non-destructive test conditions |

Passing a structural Markdown validator is not evidence that code runs or
facts are current.

## Evidence workflow

1. List mutable claims and classify code blocks before drafting.
2. Declare the target version. If no version is available and current sources
   cannot be accessed, write version-neutral material and avoid unstable
   specifics.
3. Prefer primary sources:
   - official specifications and standards;
   - official documentation for the target version;
   - official release notes or migration guides;
   - version-matched project source.
4. Use independent secondary sources only for explanation or corroboration,
   not as the sole evidence for mutable implementation details.
5. Record the exact command used for compiled, executed, or smoke-tested
   examples.
6. Separate completed checks from planned or unavailable checks.

For version-sensitive guides, include an `Applicable version` note and a
`References` section or inline links. Timeless conceptual guides do not need a
ceremonial references section unless the user requests citations.

## Code verification

### Illustrative fragments

Check that:

- syntax matches the declared language and version;
- every identifier is either defined, conventional, or explicitly omitted;
- comments and prose describe what the code actually does;
- the block is labeled as incomplete and no execution claim is made.

### Complete examples not yet verified

Use this state when the example includes its dependencies, configuration,
complete definitions, verification command, and expected result, but the
evidence gate has not completed. Record:

- dependency coordinates that were not resolved;
- the command that was not run or failed;
- the exact blocker or failure.

Unresolved dependency coordinates prevent the runnable label.

### Verified runnable examples

Use the real toolchain when it is already available. Capture:

- dependency/version resolution;
- compile, test, or run command;
- exit status and relevant observable output;
- any environment assumption required to reproduce the result.

Do not install dependencies, start external services, contact production
systems, or run untrusted code without the authority required by the current
environment. Use a sandbox and synthetic data where execution is allowed.

### Framework examples

Verify the smallest integrated behavior that supports the guide's claim.
Compilation alone is insufficient when dependency injection, lifecycle,
configuration binding, routing, serialization, or startup behavior is the
subject.

## Failure and fallback

When verification cannot be completed:

1. Preserve the guide's useful, verified content.
2. Remove or soften claims that depend on unavailable evidence.
3. Classify a complete unexecuted example as `Complete example, not yet
   verified`; use `Illustrative fragment` only when it intentionally omits
   required parts.
4. List each remaining item and the exact blocker under `Unverified`.
5. Never infer success from a plausible-looking command, HTTP status, import,
   class name, or remembered version.

Use this delivery shape:

```markdown
## Verification summary

- Structure: `validate_guide.py` passed / not run and why
- Code: command and result for each runnable example
- Sources: primary references used for mutable claims
- Unverified: item, risk, and blocker
```

If there are no unverified items, write `Unverified: none` only after the
checks above have actually completed.
