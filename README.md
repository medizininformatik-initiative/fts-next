# SMITH FHIR Transfer Services (FTSnext)

[![Build](https://img.shields.io/github/actions/workflow/status/medizininformatik-initiative/fts-next/build.yml?logo=refinedgithub&logoColor=white)](https://github.com/medizininformatik-initiative/fts-next/actions/workflows/build.yml)
[![Code Coverage](https://img.shields.io/codecov/c/github/medizininformatik-initiative/fts-next?logo=codecov&logoColor=white&label=codecov)](https://codecov.io/gh/medizininformatik-initiative/fts-next)
[![Documentation](https://img.shields.io/website?url=https%3A%2F%2Fmedizininformatik-initiative.github.io%2Ffts-next&up_message=online&up_color=blue&down_message=offline&logo=readthedocs&logoColor=white&label=docs)](https://medizininformatik-initiative.github.io/fts-next)
[![Renovate](https://img.shields.io/badge/renovate-enabled-violet.svg?logo=renovate&logoColor=white)](https://github.com/medizininformatik-initiative/fts-next/issues/67)
[![OpenSSF Scorecard](https://img.shields.io/ossf-scorecard/github.com/medizininformatik-initiative/fts-next?logo=linuxfoundation&label=ossf%20scorecard)](https://scorecard.dev/viewer/?uri=github.com/medizininformatik-initiative/fts-next)
[![OpenSSF Best Practices](https://img.shields.io/cii/percentage/10434?logo=linuxfoundation&label=ossf%20best%20practices)](https://www.bestpractices.dev/projects/10434)

FTSnext (FHIR Transfer Services) is a software system that securely transfers
healthcare data between clinical and research domains using FHIR
([Fast Healthcare Interoperability Resources][fhir]) standards. While initially
designed for the [SMITH][smith] architecture, its framework is adaptable for broader
applications in any healthcare data sharing scenario.

## Overview

At its core are three components — Clinical Domain Agent, Research Domain Agent, and
Trust Center Agent — working together to maintain privacy by ensuring the Trust Center
manages IDs without accessing medical content, while researchers receive only the data
they need without the ability to link it back to clinical sources. This elegant design
enables valuable research while robustly protecting patient privacy.

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

Please see the [official docs][docs] page for documentation on how to install and use FTSnext.
Feel free to contribute by providing feedback or develop the software, see our
[contributing guidelines][contrib] on how to participate.

[fhir]: https://fhir.org/

[smith]: https://www.smith.care

[docs]: https://medizininformatik-initiative.github.io/fts-next

[contrib]: https://medizininformatik-initiative.github.io/fts-next/contributing/contributing.html
