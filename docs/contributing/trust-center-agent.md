# Trustcenter Agent (TCA)

## Consent

The TCA offers an endpoint to receive a cohort of consented patients
from [gICS](https://www.ths-greifswald.de/forscher/gics/).

## De-Identification

The de-identification process maps the original resource IDs (oID) from the clinical domain to
pseudonyms (sID) in the research domain and shifts all dates by a random (but for each patient
fixed) duration.
For consistency, secure IDs and date shift values must remain stable across transfers, i.e.
multiple transfers of the same original IDs must generate identical secure IDs and date shift
values.

The role of the TCA consists of several parts:
First, it provides a mechanism that replaces the oIDs of the clinical domain with pseudonyms
in the research domain, in such a way that it impedes the re-identification
from the respective other domain.
Then, it manages a mechanism to time-shift dates to further improve the pseudonymization.
Last, the TCA enables to re-identify patients, if necessary.

### DateShift-ID Pattern

To ensure that date values never leave the clinical domain in their original form, FTSnext uses
the DateShift-ID Pattern. Instead of shifting dates directly at the CDA, transport IDs (tIDs) are
generated for each unique date value and attached as FHIR extensions. The actual date values are
nulled before transmission.

For details on the transfer process, see the [De-Identification](../details/deidentification)
documentation.

### FhirMappingProvider

This section describes the implementation details of the FhirMappingProvider.

The de-identification process works by generating a pseudonym (sPID) in gPAS
for the patient resource's ID (PID).
The PID may be thought of as the main ID and is used to re-identify patients.
All other IDs are hashed with SHA256.
Since we have no influence about the ids' length
and to suppress re-identification from the clinical domain,
we use gPAS to generate a second pseudonym that we use as salt for the hash function.
In gPAS the key of the salt pseudonym is named "Salt_" + oID.

To generate the date shift values a third pseudonym is generated
in gPAS with the key named "DateShiftSeed_" + oPID.
The pseudonym is used as seed for a random number generator that generates the date shift values.

After deidentification, the CDA sends a transport mapping request to the TCA containing:
- ID pairs (originalID, tID) for each resource ID
- Date pairs (tID, originalDate) for each unique date value

The TCA then:
1. Generates a pseudonym (sPID) for the patient identifier via gPAS (used for re-identification)
2. Computes a secure ID (sID) for all other IDs by hashing with a salt
3. Computes shifted dates for each original date using the patient's date shift seed
4. Stores the research mapping in Redis, including:
   - ID mappings: tID→sID (hashed with salt)
   - Date mappings: ds:tID→shiftedDate (prefixed with `ds:`)
5. Returns only the `transferId` to the CDA

The RDA then requests the secure mapping using the transfer ID. The TCA returns:
- ID mappings: tID→sID
- Date mappings: tID→shiftedDate (extracted from `ds:`-prefixed Redis entries)

The RDA resolves the tID extensions to shifted dates and removes the extensions.

```mermaid
sequenceDiagram
    CDA ->> CDA: deidentify bundle, generate tIDs on-the-fly
    CDA ->> TCA: Map<oID, tID>, oPID, Map<tID, date>
    TCA ->> gPAS: oPID, Salt_oID, Seed_dateShift_oPID
    gPAS ->> TCA: oPID ➙ sPID, Salt_oID ➙ Salt, Seed ➙ dateShiftSeed
    TCA ->> TCA: compute sIDs and shiftedDates
    TCA ->> Redis: store tID➙sID and ds:tID➙shiftedDate
    TCA ->> CDA: transferId
    CDA ->> RDA: transferId & Bundles (with tID extensions)
    RDA ->> TCA: transferId
    TCA ->> Redis: fetch mappings
    Redis ->> TCA: tID➙sID, ds:tID➙shiftedDate
    TCA ->> RDA: tID➙sID, tID➙shiftedDate
    RDA ->> RDA: resolve tIDs to sIDs and dates
```
