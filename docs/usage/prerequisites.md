# Prerequisites

FTSnext requires access to
[gICS](https://www.ths-greifswald.de/forscher/gics),
[gPAS](https://www.ths-greifswald.de/forscher/gpas/),
and a redis compatible key-value store e.g. [Valkey](https://valkey.io/).

Furthermore, gPAS needs three preset domains.
One for the mappings:

- patient ID to pseudonyms (PID -> sPID)
- resource IDs to salt values for hashing (RID -> salt)
- patient ID to date shift salt values (PID -> date shift salt)

As the keys are namespaced, it is possible to use **one** domain for all mappings.

Please see [here](../details/deidentification) for further explanation. 
