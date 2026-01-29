# Clinical Domain Agent (CDA)

## Cohort Selector

A cohort is a list of Patients that have given their consent.
At the moment there are two ways to select a cohort.

1. Set a fixed list of the patients' ID
2. Receive the cohort from gICS

## Data Selector

Request all resources of a patient.

### Fhir Resolve Service

## Deidentificator

Remove identifying data, replace IDs with transport IDs, and handle date shifting via
the DateShift-ID Pattern.

The CDA:
1. Scrapes IDs and date values from the patient bundle
2. Generates transport IDs (tIDs) for each unique date value
3. Requests transport mappings from the TCA (including tID→date mappings)
4. Attaches tID extensions to date elements using the URL
   `https://fts.smith.care/fhir/StructureDefinition/date-shift-transport-id`
5. Nulls the original date values before sending to the RDA

For more details, see the [De-Identification](../details/deidentification) documentation.

## Bundle Sender

Send patient bundle to RDA.
