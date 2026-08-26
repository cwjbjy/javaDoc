# 2026-08-21 release gate

- Seven isolated, read-only behavior scenarios completed: progressive OAuth,
  Redis reference, Kubernetes hybrid guide, CAP concept guide, Spring Security
  migration, repository-aware OAuth login, and Spring Boot 4 HTTP client
  selection.
- The OAuth and Kubernetes scenarios were rerun after two narrow corrections:
  four or more content H2 sections require a table of contents, and an
  unaccessed citation must not be reported as a checked source.
- The Spring Boot 4 scenario selected `RestClient` for a synchronous blocking
  request and `WebClient` for a streaming, non-blocking request; it retained
  `RestTemplate` only as a migration path.
- The seven structural-validator unit tests, resource-link check, skill package
  validator, and production-package manifest check passed.

Behavior runs were deliberately sequential and low-resource. Raw responses,
temporary package files, and local dependency caches were removed with the
temporary evaluation workspace.
