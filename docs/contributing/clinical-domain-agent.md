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
1. Deidentifies the bundle in a single pass, generating transport IDs (tIDs) on-the-fly for
   each unique ID and date value, attaching tID extensions and nulling date values during
   deidentification
2. Sends the generated mappings — ID pairs (originalID, tID) and date pairs
   (tID, originalDate) — to the TCA for secure storage
3. Receives `transferId` from TCA and forwards it with the deidentified bundle to the RDA

For more details, see the [De-Identification](../details/deidentification) documentation.

## Bundle Sender

Send patient bundle to RDA.
