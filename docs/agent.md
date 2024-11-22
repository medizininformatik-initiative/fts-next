# Agent Configuration

Agent configuration is mainly realized using the `application.yaml` file. The deployment bundle 
(see [Installation](./usage/installation)) contains such a file for each agent with extensive 
documentation. Each configuration entry contains a link to the documentation pages listed below:

* [Projects](./configuration/projects)
* [Runner](./configuration/runner)
* [Logging](./configuration/logging)
* [SSL](./configuration/ssl-bundles)
* [Server](./configuration/server)
* [Security](./configuration/security)
* [Observability](./configuration/observability)
* [De-Identification](./configuration/de-identification)
* [Consent](./configuration/consent)

_Note: Not all configuration options are applicable for all agents. This will be indicated by 
badges on page titles, and configuration options_

## More Configuration

FTSnext uses Spring Boot. Not all options that are built into spring boot itself are documented 
here. If you feel significant options are missing or should be documented, please consider filing 
a pull request.
