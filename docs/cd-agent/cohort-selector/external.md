# External Cohort Selector <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.3" />

The External Cohort Selector uses all provided PIDs without explicit consent checks.

::: tip
The process must be started with a manually provided cohort, see [execution](../../usage/execution#manual-cohort) for
details. Running it in `All Consented Patients` mode will not work.

```bash
curl -sSf --data '["id1", "id2", "id3"]' -H "Content-Type: application/json" \
  "https://cd-agent:8080/api/v2/process/example/start"
```

:::

::: tip
The External Cohort Selector also only works, if `ignoreConsent` in [dataSelector](../data-selector) is set to `true`,
as no consented period can be extracted from the PIDs.
:::

## Example Configuration

```yaml
external:
  patientIdentifierSystem: "https://example.org/fhir/identifiers/Patient"
```

### `patientIdentifierSystem` <Badge type="warning" text="Since 5.3" />

* **Description**: Adds a patient identifier system to the external PIDs.
* **Type**: String
* **Example**:
  ```yaml
    patientIdentifierSystem: "https://example.org/fhir/identifiers/Patient"

## Notes

* The `patientIdentifierSystem` field should reference a valid FHIR system.
