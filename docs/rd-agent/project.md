# Research Domain Project

A project in the scope of the research domain describes the arrival of FHIR data in the research
domain and its transformation and injection into the health data store.

The following steps are conducted inside the RDA to achieve this functionality:

* **Deidentification**  
  Pseudonymized data from the clinical domain is again pseudonymized using the prepared second-level
  pseudonyms and dates are shifted.

* **Sending**  
  Data is uploaded bundle-wise to the health data store.

The following section describes the configuration of a research domain project using a yaml
configuration file.

## Configuration

By creating a yaml configuration file inside the [projects folder](../configuration/projects) a
project is defined. It can later be referenced in the API using the filename without extension.
The deployment bundle (see [Installation](../usage/installation)) contains an `example.yaml`
project file that serves as a starting point for configuring a project, as it contains extensive
documentation for the configuration options.

The project file should contain the following entries, see the links below for documentation.

* [`deidentificator`](./deidentificator)
* [`bundleSender`](./bundle-sender)
