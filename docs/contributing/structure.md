# Repository Structure

The project follows a structured organization to enhance readability and maintainability.

- `api/`
  The API of FTSnext.

- `docs/`  
  Markdown files with examples and detailed documentation for users and developers. Includes user
  guides, developer guides, release steps, and more.

- [clinical-domain-agent/](clinical-domain-agent)  
  Java code, Dockerfile, CI config snippets, and Maven configuration (`pom.xml`) for the Clinical
  Domain Agent.

- `research-domain-agent/`  
  Java code, Dockerfile, CI config snippets, and Maven configuration (`pom.xml`) for the Research
  Domain Agent.

- [trust-center-agent/](trust-center-agent)  
  Java code, Dockerfile, CI config snippets, and Maven configuration (`pom.xml`) for the Trust
  Center Agent.

- `monitoring-util/`
  A utility module to collect metrics and visualize them via Prometheus.

- `test-util/`
  Contains test utils and a FhirGenerator to generate test data.

- `util/`
  A collection of utility classes or classes shared by multiple agents.

- `.github/`  
  Contains GitHub Actions workflows and related files.
