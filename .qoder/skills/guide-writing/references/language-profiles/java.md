# Java and Spring profile

Load this profile only for Java, JVM, Spring, Jakarta, or closely related
framework topics. The generic guide contract remains authoritative; these are
ecosystem-specific verification and example conventions.

## Version scope

Declare the Java release and framework versions when the guide uses
version-sensitive language features or APIs. Align class names, package names,
architecture descriptions, defaults, and deprecation status with those exact
versions.

For migrations:

- show the current supported form in primary examples;
- label legacy names or APIs with the version in which their status changed;
- state the supported replacement;
- keep old code only when it helps the migration decision.

## Java example contract

Complete Java examples use:

- explicit imports rather than wildcard imports;
- complete method signatures without ellipses or pseudo-parameters;
- defined custom classes, interfaces, annotations, enums, constants,
  exceptions, and helper methods;
- named constants or enums instead of unexplained magic values;
- explicit dependencies and the minimum supported Java/build-tool version;
- a compile, test, or run command.

Use a small single-file example when it demonstrates the concept honestly.
Use a minimal Maven or Gradle fixture when dependencies, annotation
processing, framework startup, or module configuration are part of the claim.

An illustrative Java fragment may omit boilerplate, but label it as a fragment
and name what is omitted. A complete example remains `not yet verified` while
any Maven or Gradle coordinate is unresolved or its documented command has not
succeeded. Promote it to `Verified runnable example` only after dependency
resolution and the command both succeed.

## Spring integration checks

For verified runnable Spring examples, compilation is only the first gate.
Start the application context or run a focused integration test when the guide
claims bean creation, injection, filter ordering, security behavior, routing,
validation, serialization, transactions, or configuration binding.

Check specifically:

- constructor dependencies are resolvable and do not form cycles;
- a configuration class is not forced into a cycle through beans it declares;
- custom providers, filters, converters, or handlers are actually registered,
  not merely accepted and discarded;
- every documented branch has a concrete trigger in the example;
- response codes use framework constants where available;
- nullable values and fluent calls match the API contract;
- package names and imports match the declared framework version.

## Spring Security guidance

Treat the filter chain and authentication architecture as version-sensitive.
Use official Spring Security documentation, migration guides, API references,
or version-matched source for:

- current and deprecated filter or component names;
- security configuration APIs;
- filter-chain ordering and lifecycle claims;
- authentication manager/provider wiring;
- default behavior and generated endpoints.

Do not transplant a remembered architecture diagram across major versions.
Define topic-specific components before they appear in a flow diagram.

When presenting current and legacy forms, keep them visually separate so a
reader cannot copy legacy code believing it is recommended.

## Data-access concepts

For framework-neutral concepts such as entity, repository, transaction, or
query object, define the concept independently before showing an
implementation. Compare multiple implementations only when the comparison
helps the reader make a real choice; do not force JPA, MongoDB, and MyBatis
into every data-access guide.

Keep responsibilities explicit when two objects collaborate. For example,
distinguish predicate construction from query-level concerns such as sorting,
pagination, or limits rather than calling one object merely a “container.”

## Java-specific review

- Target Java and framework versions are explicit where needed.
- Imports, custom symbols, dependencies, and commands are complete for
  complete examples.
- No deprecated API is presented as current.
- Spring claims are supported by a context start, focused integration test, or
  an explicit unverified note.
- Examples demonstrate the claimed behavior rather than only constructing
  objects.
- Version-sensitive facts cite official, version-matched sources.
