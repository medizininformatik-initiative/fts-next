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
```

## Fields

### `deidentifhir` <Badge type="warning" text="Since 5.0" />

This implementation deidentifies FHIR bundles with the Deidentifier engine of FTS. The engine reads
profiles in the configuration format of [DeidentiFHIR](https://github.com/UMEssen/DeidentiFHIR).

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
  path must be mounted into the container. This file is used for deidentification.
* **Type**: String
* **Example**:
  ```yaml
    deidentifhirConfig: /custom/path/CDtoTransport.profile
  ```

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
  The `deidentifhirConfig` is now the only configuration file needed.

## Profile Rules

The profile in `deidentifhirConfig` uses the module format of DeidentiFHIR. The engine applies it as
follows.

### What is kept

* Only the paths in the `base` list of a matched module are kept. Everything else is removed.
* A `paths` or `types` handler only transforms an element that `base` keeps. A `types` handler does
  not keep an element on its own.

### Handler order

* On one element, the `types` handlers run before the `paths` handlers.
* A module has at most one handler per path.
* If two modules for the same resource type put a handler on the same path, the order of those two
  handlers is not defined. Avoid this.

### Checks at startup

The agent rejects the profile at startup, before any data moves, when:

* a handler does not accept the FHIR type of its path or of its `types` entry, e.g. a string handler
  on `Coding.system`, which is a `uri`;
* a handler is registered on a path that names no FHIR element;
* an `identifier.system` pattern names a resource type without an `identifier`;
* a handler would run after `shiftDateHandler` on the same element. `shiftDateHandler` removes the
  date value, so a later handler, e.g. `generalizeDateHandler`, has nothing to work on. To generalize
  and shift a date, put `generalizeDateHandler` in `types` and `shiftDateHandler` in `paths`.

### Module patterns

* A `meta.profile contains '<canonical>'` pattern without a version also matches a resource that
  claims a versioned profile, `<canonical>|<version>`. A pattern with a version matches that version
  only.

### References

`referenceReplacementHandler` handles these reference forms:

* `Type/id`: the id is pseudonymized.
* `Type/id/_history/n`: the id is pseudonymized and the version is removed.
* `Type?identifier=system|value`: the identifier value is pseudonymized.
* `urn:uuid:<uuid>`: the resource type comes from the bundle entry with that `fullUrl`. The result is
  `Type/<pseudonym>`. A urn that no entry of the bundle has is removed.
* `#id` (contained resource): kept as it is.
* A reference element without a value, e.g. one with only a data-absent-reason extension, is kept as
  it is.

An absolute reference, e.g. `https://fhir.example/fhir/Condition/c9`, is rejected, even when it
points to the own FHIR server. The transfer of the page that contains it fails. Store references in
relative form in the clinical FHIR server.

## Notes

* Ensure all domains (`pseudonym`, `salt`, and `dateShift`) are correctly configured in the TCA.
* To protect pseudonyms against brute-force attacks, it is essential to choose a sufficiently large
  alphabet and salt length. This ensures that the total number of possible combinations ($A^n$) is
  high enough to make reverse computation practically infeasible.
* The `maxDateShift` must be in a valid ISO-8601 duration format. Refer
  to [ISO-8601 documentation](https://en.wikipedia.org/wiki/ISO_8601) for more details.
* Mount the configuration file (`deidentifhirConfig`) into the Docker container if the agent runs in
  a containerized environment. Ensure the path is accessible to the agent at runtime.
