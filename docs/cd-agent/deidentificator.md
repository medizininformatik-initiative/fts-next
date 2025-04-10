# Deidentificator <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.0" />

This document describes the configuration options available for managing deidentification settings
in the `deidentificator` section of the project configuration file.

::: danger Security Warning
To protect pseudonyms against brute-force attacks, it is essential to choose a sufficiently large
alphabet and salt length.
This ensures that the total number of possible combinations ($A^n$) is high enough to make reverse
computation practically infeasible.
See [Pseudonymization](../details/pseudonymisierung) for more details.
:::

## Configuration Example

The `deidentificator` section allows different implementations to be used for pseudonymizing and
anonymizing patient data. At the moment there is only one implementation available out-of-the-box:
`deidentifhir`

```yaml
deidentificator:
  deidentifhir:
    trustCenterAgent:
      server:
        baseUrl: http://tc-agent:8080
        auth: [ ... ]
        ssl: [ ... ]
      domains:
        pseudonym: MII
        salt: MII
        dateShift: MII
    maxDateShift: P14D
    dateShiftPreserve: NONE
    deidentifhirConfig: /app/config/deidentifhir/CDtoTransport.profile
    scraperConfig: /app/config/deidentifhir/IDScraper.profile
```

## Fields

### `deidentifhir` <Badge type="warning" text="Since 5.0" />

This implementation uses [deidentifhir](https://github.com/UMEssen/DeidentiFHIR) to accomplish
deidentification of FHIR bundles.

#### `trustCenterAgent.server` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies connection settings for the Trust Center Agent (TCA) server used for
  deidentification operations.
* **Type**: [`HttpClientConfig`](../types/HttpClientConfig)
* **Example**:
  ```yaml
    trustCenterAgent:
      server:
        baseUrl: http://custom-tc-agent:9000
        auth: [ ... ]
        ssl: [ ... ]
  ```

#### `trustCenterAgent.domains.pseudonym` <Badge type="warning" text="Since 5.0" />

* **Description**: The TCA domain where pseudonyms are stored.
* **Type**: String
* **Example**:
  ```yaml
    trustCenterAgent:
      domains:
        pseudonym: MII_PSEUDONYMS
  ```
* **Important**: This domain must already exist in gPAS before FTSnext can use it. FTSnext cannot
  create or alter domains.

#### `trustCenterAgent.domains.salt` <Badge type="warning" text="Since 5.0" />

* **Description**: The TCA domain where salts are stored.
* **Type**: String
* **Example**:
  ```yaml
    trustCenterAgent:
      domains:
        salt: MII_SALT
  ```
* **Important**: This domain must already exist in gPAS before FTSnext can use it. FTSnext cannot
  create or alter domains.

#### `trustCenterAgent.domains.dateShift` <Badge type="warning" text="Since 5.0" />

* **Description**: The TCA domain where the seeds for generating date shift values are stored.
* **Type**: String
* **Example**:
  ```yaml
    trustCenterAgent:
      domains:
        dateShift: MII_DATE_SHIFT
  ```
* **Important**: This domain must already exist in gPAS before FTSnext can use it. FTSnext cannot
  create or alter domains.

#### `maxDateShift` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies the maximum date shift, defined as an ISO-8601 duration.
* **Type**: String
* **Example**:
  ```yaml
    maxDateShift: P30D
  ```

#### `dateShiftPreserve` <Badge type="warning" text="Since 5.2" />

* **Description**: Specifies whether the weekday or the time of day are preserved.
  Possible values: NONE (default), WEEKDAY, DAYTIME
* **Type**: [`DateShiftPreserve`](../types/DateShiftPreserve)
* **Example**:
  ```yaml
    dateShiftPreserve: WEEKDAY
  ```

#### `deidentifhirConfig` <Badge type="warning" text="Since 5.0" />

* **Description**: Path to the DeidentiFHIR configuration file. If using a Docker container, the
  path must be mounted into the container.
* **Type**: String
* **Example**:
  ```yaml
    deidentifhirConfig: /custom/path/CDtoTransport.profile
  ```

#### `scraperConfig` <Badge type="warning" text="Since 5.0" />

* **Description**: Path to the scraper configuration file used by DeidentiFHIR. If using a Docker
  container, the path must be mounted into the container.
* **Type**: String
* **Example**:
  ```yaml
    scraperConfig: /custom/path/IDScraper.profile
  ```

## Notes

* Ensure all domains (`pseudonym`, `salt`, and `dateShift`) are correctly configured in the TCA.
* To protect pseudonyms against brute-force attacks, it is essential to choose a sufficiently large
  alphabet and salt length. This ensures that the total number of possible combinations ($A^n$) is
  high enough to make reverse computation practically infeasible.
* The `maxDateShift` must be in a valid ISO-8601 duration format. Refer
  to [ISO-8601 documentation](https://en.wikipedia.org/wiki/ISO_8601) for more details.
* Mount the configuration files (`deidentifhirConfig` and `scraperConfig`) into the Docker container
  if the agent runs in a containerized environment. Ensure the paths are accessible to the agent at
  runtime.
