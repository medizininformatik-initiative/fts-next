# Agent Configuration

Agent configuration in the context of FTSnext means the configuration of the server components,
like ports, certificates, logging, etc.

Agent configuration is mainly realized using the `application.yaml` file. The deployment bundle
(see [Installation](./usage/installation)) contains such a file for each agent with extensive
documentation. Each configuration entry contains a link to the documentation pages listed below:

## Common Configuration Options

* [Projects](./configuration/projects)
* [Runner](./configuration/runner)
* [Logging](./configuration/logging)
* [SSL](./configuration/ssl-bundles)
* [Server](./configuration/server)
* [Security](./configuration/security)
* [Observability](./configuration/observability)

_Note: Not all configuration options are applicable for all agents. This will be indicated by
badges on page titles, and configuration options_

## Trust Center Agent Specifics:

As the trust center agent acts as a facade and relies upon backend services like [gICS][gics]
and [gPAS][gpas] to realize consent and pseudonym handling, its configuration differs slightly from
clinical domain and research domain agent. While all configuration
options [above](#common-configuration-options) can be applied, there are specifics that only apply
to the trust center.

* [De-Identification](./configuration/de-identification)
* [Consent](./configuration/consent)

## More Configuration

FTSnext uses Spring Boot. Not all options that are built into spring boot itself are documented
here. If you feel significant options are missing or should be documented, please consider filing
a pull request.

[gics]: https://www.ths-greifswald.de/forscher/gics/
[gpas]: https://www.ths-greifswald.de/forscher/gpas/