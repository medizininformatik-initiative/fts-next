# Deidentificator <Badge type="tip" text="Clinical Domain Agent" />

This document describes the configuration options available for managing deidentification settings
in the `deidentificator` section of the project configuration file.

## Configuration Structure

The `deidentificator` section allows different implementations to be used for pseudonymizing and 
anonymizing patient data. At the moment there is only one implementation available out-of-the-box:
`deidentifhir`

```yaml
deidentificator:
  deidentifhir:
    trustCenterAgent:
      server:
        baseUrl: http://tc-agent:8080
      domains:
        pseudonym: MII
        salt: MII
        dateShift: MII
    maxDateShift: P14D
    deidentifhirConfig: /app/config/deidentifhir/CDtoTransport.profile
    scraperConfig: /app/config/deidentifhir/IDScraper.profile
```

## Fields

### `deidentifhir`

This implementation uses [deidentifhir](https://github.com/UMEssen/DeidentiFHIR) to accomplish 
deidentification of FHIR bundles.

#### `trustCenterAgent.server`

* **Description**: Specifies connection settings for the Trust Center Agent (TCA) server used for
  deidentification operations.
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
* **Example**:
  ```yaml
    trustCenterAgent:
      server:
        baseUrl: http://custom-tc-agent:9000
  ```

#### `trustCenterAgent.domains.pseudonym`

* **Description**: The TCA domain where pseudonyms are stored.
* **Type**: String
* **Example**:
  ```yaml
    trustCenterAgent:
      domains:
        pseudonym: MII_PSEUDONYMS
  ```

#### `trustCenterAgent.domains.salt`

* **Description**: The TCA domain where salts are stored.
* **Type**: String
* **Example**:
  ```yaml
    trustCenterAgent:
      domains:
        salt: MII_SALT
  ```

#### `trustCenterAgent.domains.dateShift`

* **Description**: The TCA domain where date shift values are stored.
* **Type**: String
* **Example**:
  ```yaml
    trustCenterAgent:
      domains:
        dateShift: MII_DATE_SHIFT
  ```

#### `maxDateShift`

* **Description**: Specifies the maximum date shift, defined as an ISO-8601 duration.
* **Type**: String
* **Example**:
  ```yaml
    maxDateShift: P30D
  ```

#### `deidentifhirConfig`

* **Description**: Path to the DeidentiFHIR configuration file. If using a Docker container, the
  path must be mounted into the container.
* **Type**: String
* **Example**:
  ```yaml
    deidentifhirConfig: /custom/path/CDtoTransport.profile
  ```

#### `scraperConfig`

* **Description**: Path to the scraper configuration file used by DeidentiFHIR. If using a Docker
  container, the path must be mounted into the container.
* **Type**: String
* **Example**:
  ```yaml
    scraperConfig: /custom/path/IDScraper.profile
  ```

## Notes

* Ensure all domains (`pseudonym`, `salt`, and `dateShift`) are correctly configured in the TCA.
* The `maxDateShift` must be in a valid ISO-8601 duration format. Refer
  to [ISO-8601 documentation](https://en.wikipedia.org/wiki/ISO_8601) for more details.
* Mount the configuration files (`deidentifhirConfig` and `scraperConfig`) into the Docker container
  if the agent runs in a containerized environment. Ensure the paths are accessible to the agent at
  runtime.
