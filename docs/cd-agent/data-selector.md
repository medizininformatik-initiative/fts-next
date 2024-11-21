# Data Selector <Badge type="tip" text="Clinical Domain Agent" />

This document describes the configuration options available for managing data selection in the
`dataSelector` section of the project configuration file. The `dataSelector` section defines
parameters for fetching and resolving patient records from a FHIR server.

## Configuration Structure

The `dataSelector` section allows different implementations to be used for selecting transfer data,
at the moment there is only one implementation available out-of-the-box: `everything`

```yaml
dataSelector:
  everything:
    fhirServer:
      baseUrl: http://cd-hds:8080/fhir
    resolve:
      patientIdentifierSystem: http://fts.smith.care
```

## Fields

### `everything`

The "everything" data selector uses the FHIR servers `patient/$everything` operation to fetch data.

#### `fhirServer.baseUrl`

* **Description**: Specifies the base URL of the FHIR server endpoint used for fetching patient
  records.
* **Type**: String
* **Example**:
  ```yaml
    fhirServer:
      baseUrl: http://my-fhir-server:8080/fhir
  ```

#### `resolve.patientIdentifierSystem`

* **Description**: Defines the system URL used to resolve patient identifiers within the FHIR
  server.
* **Type**: String
* **Example**:
  ```yaml
    resolve:
      patientIdentifierSystem: http://custom.identifier.system
  ```

## Notes

* The `baseUrl` field must be a valid URL pointing to a compliant FHIR server capable of handling
  the `$everything` operation.
* The `patientIdentifierSystem` field must be a valid system URL used for resolving patient
  identifiers in the FHIR server.
* Ensure the FHIR server endpoint is accessible and configured correctly for data retrieval.

## References

* [Operation $everything on Patient](https://www.hl7.org/fhir/R4/patient-operation-everything.html)
  * [Blaze Documentation](https://samply.github.io/blaze/api/operation-patient-everything)
