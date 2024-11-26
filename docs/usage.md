# FHIR Transfer Services Usage Guide

Welcome to the FHIR Transfer Services user guide! This guide will help you get started with running
the FHIR Transfer Services components: Clinical Domain Agent (CDA),
Research Domain Agent (RDA), and Trust Center Agent (TCA). 

## Getting Started

1. [Prerequisites](./usage/prerequisites)
2. [Installation](./usage/installation)
3. [Configuration](./usage/configuration)
4. [Execution](./usage/execution)

## Overview

FTSnext is built for the transfer of FHIR data from the clinical domain to the research domain
while ensuring the Patients' privacy. Therefore, the data are de-identified by removing specific
data, replacing IDs with pseudonymized IDs, and shifting the dates by a random value.

The following sequence diagram gives an overview of FTSnext's functionality.

```mermaid
sequenceDiagram
    CDA ->> TCA: request consented Patients
    TCA ->> CDA: List of Patient IDs

    loop for each Patient ID
        CDA ->> CDA: deidentify Patient
        CDA ->> TCA: request Transport Mapping
        TCA ->> CDA: Transport Mapping and Date Shift Value
        CDA ->> RDA: Patient Bundle
        RDA ->> TCA: request Research Mapping
        TCA ->> RDA: Research Mapping
        RDA ->> RDA: deidentify Patient
    end
```
