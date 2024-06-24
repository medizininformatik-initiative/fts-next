# FTS Research Domain Agent Development

## Configuration

```yaml
projectConfigurations:
  # The project's id.
  smith:
    # The top level domain that will be used in gPAS to store the pseudonyms.
    gpasDomain: smith
    pseudonymization:
      # make sure that you adapt these example profiles to your needs. otherwise sensitive data might be leaking!
      scraperConfigFile: ./conf/deidentiFHIR/smith-rd/kds/profiles/IDScraper.profile
      pseudonymizationConfigFile: ./conf/deidentiFHIR/smith-rd/kds/profiles/TransportToRD.profile
    # Specification of the FHIR Server that will be used to store the de-identified FHIR resources.
    fhirTarget:
      use: true
      resturl: http://localhost:8083/fhir
      auth-method: NONE
```
