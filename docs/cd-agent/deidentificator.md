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
`deidentifier`

```yaml
deidentificator:
  deidentifier:
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
    deidentifierConfig: /app/config/deidentifier/CDtoTransport.profile
```

## Fields

### `deidentifier` <Badge type="warning" text="Since 5.8" />

This implementation de-identifies each `searchset` page of a patient with the `deidentifier`
module of FTSnext. The profile uses the HOCON format of
[DeidentiFHIR](https://github.com/UMEssen/DeidentiFHIR), see [Profile Check](#profile-check).

::: warning Deprecated key
Before 5.8, this implementation had the key `deidentifhir`. The CDA still accepts the key
`deidentifhir` and logs a warning when it loads the project. Use `deidentifier`, because a later
release removes the old key.
:::

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

#### `deidentifierConfig` <Badge type="warning" text="Since 5.8" />

* **Description**: Path to the profile that the deidentifier uses. If the agent runs in a Docker
  container, mount the path into the container.
* **Type**: String
* **Example**:
  ```yaml
    deidentifierConfig: /custom/path/CDtoTransport.profile
  ```
* **Note**: Before 5.8, this field had the name `deidentifhirConfig`. The CDA still accepts the
  old name. Use `deidentifierConfig`, because a later release removes the old name.

#### `scraperConfig` <Badge type="warning" text="Since 5.0" /> <Badge type="danger" text="Removed since 5.5" />

* **Description**: Path to the scraper configuration file previously used by DeidentiFHIR for a
  separate ID scraping pass.
* **Type**: String
* **Example**:
  ```yaml
    scraperConfig: /custom/path/IDScraper.profile
  ```
* **Note**: This field has been removed in version 5.5. The separate scraping pass has been
  replaced by single-pass deidentification, which generates transport IDs on-the-fly.
  The `deidentifierConfig` is now the only configuration file needed.

## Profile Check <Badge type="warning" text="Since 5.8" /> {#profile-check}

The CDA reads the profile when it loads the project. If the profile has an error, the project does
not load, and the log shows the error. Before 5.8, the CDA accepted these profiles and found the
error only during a transfer, or not at all:

| Profile                                                                                                    | Before 5.8                                                         |
|------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| A `base` path names an unknown element, has the wrong case, or names a choice element without `[type]`     | Ignored the path                                                   |
| A `base` path names a composite element (e.g. `Patient.gender.extension`) or a narrative (`Patient.text`) | Ignored the path, but kept `text.status` for `Patient.text.status` |
| An `identifier.system` pattern on a resource type without `identifier`                                    | Failed when such a resource arrived                                |
| A `types` `shiftDateHandler` and a `paths` date handler on the same element                                | Shifted the date                                                   |
| A handler for another FHIR type than the element                                                           | Failed every page that reached the element                         |
| Two `paths` keys of one module reach the same path                                                         | Ran both handlers, in the order of the keys                        |
| A `paths` glob matches no `base` path                                                                      | Ignored the glob                                                   |
| A `types` key is not a concrete FHIR primitive type (e.g. `BaseDateTimeType`, `Coding`)                    | Ignored the key                                                    |
| A handler name that the CDA does not know, e.g. `conditionalReferencesReplacementHandler`                  | Accepted `conditionalReferencesReplacementHandler`                 |

To fix such a profile, remove or correct the entry that the log names.

## Notes

* Ensure all domains (`pseudonym`, `salt`, and `dateShift`) are correctly configured in the TCA.
* To protect pseudonyms against brute-force attacks, it is essential to choose a sufficiently large
  alphabet and salt length. This ensures that the total number of possible combinations ($A^n$) is
  high enough to make reverse computation practically infeasible.
* The `maxDateShift` must be in a valid ISO-8601 duration format. Refer
  to [ISO-8601 documentation](https://en.wikipedia.org/wiki/ISO_8601) for more details.
* Mount the configuration file (`deidentifierConfig`) into the Docker container if the agent runs in
  a containerized environment. Ensure the path is accessible to the agent at runtime.
