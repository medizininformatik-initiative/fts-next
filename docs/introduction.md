# Introduction

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

[fhir]: https://fhir.org/

[smith]: https://www.smith.care
