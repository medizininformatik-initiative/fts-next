# SMITH FHIR Transfer Services (FTSnext)

[![Build](https://github.com/medizininformatik-initiative/fts-next/actions/workflows/build.yml/badge.svg)](https://github.com/medizininformatik-initiative/fts-next/actions/workflows/build.yml)
[![Code Coverage](https://codecov.io/gh/medizininformatik-initiative/fts-next/branch/main/graph/badge.svg)](https://codecov.io/gh/medizininformatik-initiative/fts-next)
[![Documentation](https://img.shields.io/badge/see-Docs-blue.svg)](https://medizininformatik-initiative.github.io/fts-next)
[![Renovate](https://img.shields.io/badge/renovate-enabled-violet.svg)](https://github.com/medizininformatik-initiative/fts-next/issues/67)

The FHIR Transfer Services (FTSnext) facilitate the transfer of
FHIR ([Fast Healthcare Interoperability Resources][fhir]) resources between FHIR servers, inhabiting
different domains of the [SMITH][smith] architecture, namely clinical and research domain.

## Overview

The FHIR Transfer Services software project consists of several components designed to transfer FHIR
resources securely and efficiently between clinical and research domains. It includes Clinical
Domain Agent (CDA), Research Domain Agent (RDA), and Trust Center Agent (TCA), each serving a
specific role in the data transfer process.

### Clinical Domain Agent (CDA)

The CDA is designed to handle the secure deidentification, pseudonymization and transfer of clinical
FHIR resources. It ensures that sensitive patient information is appropriately anonymized before
being transferred between systems, supporting interoperability in healthcare while safeguarding
patient privacy. It plays a crucial role in the overall data transfer process, working in
collaboration with the Research Domain Agent (RDA) and Trust Center Agent (TCA) to facilitate
controlled and privacy-aware data exchange.

### Trust Center Agent (TCA)

The TCA serves as a critical component responsible for managing trust and security aspects during
the data transfer process. It plays a central role in pseudonymization and deidentification,
coordinating with external services such as gPAS and gics, and ensuring the secure exchange of
pseudonyms between the Clinical Domain Agent (CDA) and Research Domain Agent (RDA).

### Research Domain Agent (RDA)

The RDA manages the secure transfer of deidentified research-focused FHIR resources. It is
responsible for receiving and processing deidentified data from the Clinical Domain Agent (CDA) and
facilitating its integration into research FHIR servers. By ensuring compliance with the SMITH
architecture, RDA supports the interoperability of research data while preserving patient
confidentiality.

## Documentation

Please see the [official docs](https://medizininformatik-initiative.github.io/fts-next) page for
complete documentation.

[fhir]: https://fhir.org/

[smith]: https://www.smith.care
