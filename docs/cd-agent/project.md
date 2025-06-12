# Clinical Domain Project

A project in the scope of the clinical domain describes the extraction and transformation of FHIR
data until it leaves the clinical domain.

The following steps are conducted inside the RDA to achieve this functionality:

* **Cohort Selection**  
  Patient IDs are selected from a source.

* **Data Selection**  
  Data is selected for each patient.

* **Deidentification**  
  Data is pseudonymized for transportation, second-level pseudonyms are generated but cannot be
  inspected in the clinical domain.

* **Sending**  
  Data is uploaded bundle-wise to a destination outside the clinical domain.

The following section describes the configuration of a clinical domain project using a yaml
configuration file.

## Configuration

By creating a yaml configuration file inside the [projects folder](../configuration/projects) a
project is defined. It can later be referenced in the API using the filename without extension.
The deployment bundle (see [Installation](../usage/installation)) contains several `example.yaml`
project files that serves as a starting point for configuring a project, as it contains extensive
documentation for the configuration options.

The project file should contain the following entries, see the links below for documentation.

* [`cohortSelector`](./cohort-selector)
* [`dataSelector`](./data-selector)
* [`deidentificator`](./deidentificator)
* [`bundleSender`](./bundle-sender)
