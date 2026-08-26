# Behavior benchmark summary

## Outcome

- The production refactor passed 31 of 31 behavior assertions across six
  scenarios. The staged baseline passed 12 of 31.
- The official aggregator reported a mean per-scenario pass rate of 100.0% for
  the refactor versus 38.9% for the baseline, a 61.1 percentage-point gain.
- The refactor improved progressive, reference, hybrid, no-code, versioned
  Spring, and repository-aware guide behavior without forcing one structure.

## Behavior changes observed

- Fully specified requests proceeded without unnecessary outline approval.
- Removing the fixed progressive template improved reference and no-code
  scenarios without weakening causal or hybrid guides.
- The three-state example evidence contract prevented unavailable dependency
  resolution from being reported as a runnable result.
- The repository-aware scenario retained minimum local context and added a
  resolving table of contents after structural validation exposed the gap.

## Limits

- Each behavior scenario had one independently generated baseline and one
  refactored run, so the results do not estimate within-scenario variance.
- The Kubernetes scenario passed with and without the refactor and therefore
  remains a regression guard rather than a differentiating case.
- Generation timing and model-token telemetry were unavailable. Recorded token
  values were fallback output-character counts and must not be interpreted as
  model token usage.
- Official web pages and Maven dependencies were unavailable in the Spring
  run; that scenario assessed honest disclosure rather than external fact
  verification.

This summary preserves the release evidence. Raw generated responses,
per-run grading files, and HTML reports were intentionally discarded when the
temporary evaluation workspace was removed.
