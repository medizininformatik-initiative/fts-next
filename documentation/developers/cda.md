# FTS Clinical Domain Agent Development

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
      pseudonymizationConfigFile: ./conf/deidentiFHIR/smith-rd/kds/profiles/CDtoTransport.profile
    # The key of the process that will be triggered at the end of the CDA's run.
    followingProcessKey: research-domain-agent-process
```
