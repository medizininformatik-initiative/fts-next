---
aside: false
---

# FTSnext Development

Welcome to the developer documentation for the FHIR Transfer Services project! This document is
intended to guide developers through the codebase, architecture, and development practices.

***Note:** This documentation may be outdated, and we acknowledge its current limitations. We are
actively working to enhance and update this guide to provide a more comprehensive and up-to-date
resource for developers. Your feedback is valuable in this ongoing improvement process.*

```mermaid
sequenceDiagram
  box Clinical Domain
    participant cd_hds
    participant CDA
  end
  box Trustcenter Domain
    participant TCA
    participant gICS
    participant gPAS
  end
  box Research Domain
    participant RDA
    participant rd_hds
  end

  CDA ->> TCA: cd/consented-patients/{fetch,fetch-all}
  TCA ->> gICS: {$allConsentsForDomain,$allConsentsForPerson}
  gICS ->> TCA: [Patient ID]
  TCA ->> CDA: [Patient ID]

  loop Patient ID
    CDA ->> cd_hds: fetch Patient ID
    cd_hds ->> CDA: Patient
    CDA ->> CDA: deidentify Patient
    CDA ->> TCA: cd/transport-mapping(Patient ID, [ID])
    TCA ->> gPAS: generate Secure ID
    TCA ->> gPAS: generate ID Salt
      TCA ->> gPAS: generate Date Shift Seed
    TCA ->> TCA: generate [Transport ID] and Date Shift
    TCA ->> CDA: mapName, [ID -> Transport ID] and Date Shift
    CDA ->> RDA: process/{project}/patient(PatientBundle, mapName)
    RDA ->> CDA: PROCESS_ID
    RDA ->> TCA: rd/secure-mapping(mapName)
    TCA ->> RDA: [Transport ID -> Research ID], Date Shift Value
    RDA ->> RDA: deidentify Patient
    RDA ->> rd_hds: Bundle
    CDA ->> RDA: status/PROCESS_ID
    RDA ->> CDA: return Status
  end
```
