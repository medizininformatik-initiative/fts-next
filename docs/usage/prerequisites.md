# Prerequisites

FTSnext requires access to [gICS][gics], [gPAS][gpas] and a redis compatible key-value store
e.g. [Valkey](https://valkey.io/).

Furthermore, gPAS needs three preset domains. One for the mappings:

* patient ID to pseudonyms (PID -> sPID)
* resource IDs to salt values for hashing (RID -> salt)
* patient ID to date shift seed value (PID -> date shift seed)

As the keys are namespaced, it is possible to use **one** domain for all mappings.

Please see [here](../details/deidentification) for further explanation.

# Prerequisites

The Trust Center Agent is configured to work with [gICS][gics] for consent management and
[gPAS][gpas] for pseudonym management and a redis compatible key-value store
e.g. [Valkey](https://valkey.io/).

## Important: Pre-configured domains required

You must ensure that any domains referenced in your FTSnext configuration (such as in
`cohortSelector` and `deidentificator` settings) are already created and properly configured in the
respective backend systems:

* The domain referenced in `cohortSelector.trustCenterAgent.domain` must exist in your consent
  management system
* The domains referenced in `deidentificator.deidentifhir.trustCenterAgent.domains` must exist in
  your pseudonym management system for:
  * `pseudonym` - For patient ID pseudonymization
  * `salt` - For resource ID salting
  * `dateShift` - For date shifting seed values

**Note for gPAS implementations:** In gPAS, you can use a single domain for all three
pseudonymization functions (`pseudonym`, `salt`, and `dateShift`). The keys are namespaced
internally by FTSnext, so one domain can serve all three purposes if desired.

Please see [here](../details/deidentification) for further explanation on how these domains are used
in the deidentification process.

## Research Domain FHIR Store: Referential Integrity

The Research Domain Agent writes de-identified bundles to a FHIR store (e.g.
[Blaze](https://github.com/samply/blaze)). Because the agent transfers resources into the research
domain incrementally, references between resources (e.g. an `Observation` referencing a `Patient`)
may temporarily point to targets that have not yet been written. Additionally, de-identification
can rewrite references such that the referential graph intentionally differs from the source.

For this reason, the FHIR store used as the research domain's health data store must be configured
to **not enforce referential integrity**. For Blaze, this means setting:

```yaml
ENFORCE_REFERENTIAL_INTEGRITY: "false"
```

See the [Blaze documentation](https://github.com/samply/blaze/blob/master/docs/deployment/environment-variables.md)
for details. Without this setting, writes from the Research Domain Agent will fail with
referential integrity violations.

[gics]: https://www.ths-greifswald.de/forscher/gics

[gpas]: https://www.ths-greifswald.de/forscher/gpas/
