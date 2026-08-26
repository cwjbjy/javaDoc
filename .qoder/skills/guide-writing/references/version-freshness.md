# Version freshness

Read this reference whenever a guide recommends APIs, commands, configuration,
defaults, lifecycle behavior, package names, or architectural patterns that can
change between releases.

## Decide the version scope

Use the version explicitly requested by the user. If the request names a
version family, state the supported concrete range used for the guide. If the
version cannot be inferred and choosing an API would materially change the
guide, ask one consolidated question; otherwise keep the guide version-neutral
and avoid release-specific recommendations.

## Select recommendations

For each version-sensitive recommendation:

1. check official documentation, release notes, migration guidance, API
   references, specifications, or version-matched source for the declared
   version;
2. recommend the current supported pattern for that version;
3. include an older API, command, or pattern only when migration or
   compatibility is in scope, and label its status, version boundary, and
   supported replacement;
4. avoid unsupported claims when the required primary source is unavailable;
   state the item as unverified instead.

“Familiar” and “widely used” are not evidence that an API remains the current
recommendation. Do not quietly reuse examples from an older major release.

## Deliver and review

Declare the applicable version near the beginning of the guide. Cite the
primary evidence for mutable recommendations in a references section or inline.
The verification summary must identify the checked source and list any API
status, migration boundary, or current-replacement claim that could not be
verified.

During review, flag a guide when it presents a legacy API as the default,
omits the target version for a release-sensitive recommendation, or names a
replacement without target-version evidence.
