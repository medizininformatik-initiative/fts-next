# Agent Configuration

Agent configuration is mainly realized using the `application.yaml` file. The deployment bundle 
(see [Installation](../usage/installation.md)) contains such a file for each agent with extensive 
documentation. Each configuration entry contains a link to the documentation pages listed below:

* [Projects](./projects.md)
* [Runner](./runner.md)
* [Logging](./logging.md)
* [SSL](./ssl-bundles.md)
* [Server](./server.md)
* [Security](./security/index.md)
* [Observability](./observability.md)
* [De-Identification](./de-identification.md)
* [Consent](./consent.md)

_Note: Not all configuration options are applicable for all agents. This will be indicated by 
badges on page titles, and configuration options_

## More Configuration

FTSnext uses Spring Boot. Not all options that are built into spring boot itself are documented 
here. If you feel significant options are missing or should be documented, please consider filing 
a pull request.
