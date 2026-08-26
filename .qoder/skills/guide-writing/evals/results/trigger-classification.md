# Trigger classification summary

Five fresh-context repetitions classified the same 20 bilingual cases: 10
intended triggers and 10 high-value near misses per repetition.

| Description | Precision | Recall | Accuracy |
|---|---:|---:|---:|
| Staged baseline | 81.1% | 86.0% | 83.0% |
| Production refactor | 100.0% | 100.0% | 100.0% |

The production description produced 50 true positives, 50 true negatives,
zero false positives, and zero false negatives. Every repetition met the 90%
precision and recall release gate.

The staged baseline produced 43 true positives, 40 true negatives, 10 false
positives, and 7 false negatives. These values are comparison evidence, not a
release gate for the production description.

The trigger description did not change during the later instruction-routing
and version-freshness refinement, so the five expensive repetitions were not
rerun.
