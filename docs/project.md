# Project <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" />

In the context of FTSnext a *project* represents a cohesive collection of settings that
collectively define a transfer process. This process is executed by the FTSnext agents working
together.

The Clinical Domain Agent (CDA) has its own project configuration as well as the Research Domain
Agent (RDA). Both executed together should result in the extraction of fhir resources from the
clinical domain health data store, transformation (such as deidentification, or anonymization) and
upload to the research domains health data store.

See
* [Clinical Domain Project](./cd-agent/project)
* [Research Domain Project](./rd-agent/project)
