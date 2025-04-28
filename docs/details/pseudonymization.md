---
head:
- - link
  - rel: stylesheet
    href: https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.5.1/katex.min.css
---


::: info
This English text is a translation of the original German document,
[Pseudonymisierung](./pseudonymisierung), which was written to coordinate with our Data Protection
Officer.
It is provided for convenience; in case of discrepancies, the German version takes precedence.
:::

# Pseudonymization

## Requirements

1. The ID exchange process takes place via a trusted third party (TCA).
2. Re-identification by the trusted third party must be possible.
3. sIDs must remain consistent across repeated transmissions.

## Transmission Process

In a transmission process, the CDA requests the assignment of original IDs (oIDs) to transport IDs (
tIDs) from the TCA. Before the patient bundle is sent to the research domain agent (RDA), the oIDs
are replaced by tIDs.  
After receiving the transport-pseudonymized patient bundle, the RDA requests the mapping of tIDs to
stable pseudonyms (sIDs) and replaces the tIDs with sIDs.

## Generation of Transport and Pseudonymized IDs

### sID

The TCA uses gPAS to generate and store pseudonyms.
Two pseudonyms are generated for each patient:

$$
\begin{align*}
\text{patientOID} &\rightarrow \text{patientSID}\\
\textit{Salt\_} + \text{patientOID} &\rightarrow \text{Salt}
\end{align*}
$$

The keys used are the patient’s oID and the concatenation of the fixed string _Salt_ and the oID.
Note that _Salt_ is a literal string, not a variable or actual salt.

The first pseudonym replaces the oID of the patient resource—i.e., it is a direct mapping to the
patient’s sID and can be used for re-identification.
The second pseudonym is used as a salt for generating pseudonyms for the remaining resources:

$$
\text{Resource-sID} = \text{SHA256}(\text{Salt} + \text{oID})
$$

::: warning Security Note
The combination of alphabet size $A$ and salt length $n$ — that is, $A^n$ possible variants — must
be sufficiently large to be resistant to brute-force attacks (see Security Aspects).
:::

### tID

For each oID, a random number is generated to serve as the tID.  
The mapping

$$ \text{oID} \rightarrow \text{tID} $$

is temporarily stored in a key-value store, so tIDs may vary between transmissions.

## Example

Assume we have a patient with two resources:

```
Patient:
  oID = 1,
  Ressourcen:
  [
    Encounter: oID = 2,
    Medication: oID = 3
  ]
```

The CDA sends the IDs to be pseudonymized to the TCA:

$$
\begin{align*}
1 &\rightarrow \text{d7dsjdg4}\\
\text{Salt\_1} &\rightarrow \text{5kf8344f}
\end{align*}
$$

### Transport Mapping: Replacing oIDs with tIDs

Once the CDA sends the oIDs to be pseudonymized to the TCA, temporary transport IDs (tIDs) are
generated.
These tIDs replace the original oIDs before the data is forwarded to the RDA.

**Example Transport Mapping:**

$$
\begin{align*}
1 &\rightarrow 84613221\\
2 &\rightarrow 34186571\\
3 &\rightarrow 97354168
\end{align*}
$$

After this mapping, the bundle with transport-pseudonymized IDs is forwarded to the RDA:

```
transport-Patient:
  tID = 84613221,
  Ressourcen:
  [
    Encounter: tID = 34186571,
    Medication: tID = 97354168
  ]
```

### Secure Mapping

After the RDA has received the transport-pseudonymized bundle, it requests the mapping of tIDs to
stable pseudonyms (sIDs) from the TCA.
These sIDs are intended for research purposes and remain constant across repeated transmissions.

**Example Secure Mapping:**

$$
\begin{align*}
84613221 &\rightarrow \text{d7dsjdg4}\\
34186571 &\rightarrow \text{SHA256}(5kf83442)\\
97354168 &\rightarrow \text{SHA256}(5kf83443)
\end{align*}
$$

The RDA then replaces the tIDs with the sIDs:

```
research-Patient:
 <sID = d7dsjdg4,
 Ressourcen: [
  Encounter: sID = SHA256(5kf83442),
  Medication: sID = SHA256(5kf83443)
 ]
```

## Security Aspects

### Brute-Forcing the Salt

Assume an attacker knows the oIDs and sIDs and attempts to establish a relationship between them.
To do so, they try to brute-force the salt.

The time $T$ required to determine the salt is given by:

$$
T = \frac{A^n}{v}
$$

where $A$ is the alphabet size, $n$ is the salt length, and $v$ is the number of hashes per second.

With current hardware, $10^9$ hashes per second is a realistic assumption.

| Alphabet Size $(A)$ | Length $(n)$ | Possible Combinations $(Aⁿ)$        | Time at $10^9$ Hashes/Second                         |
|---------------------|--------------|-------------------------------------|------------------------------------------------------|
| $10$ (Digits)       | $8$          | $10^8$                              | $0.1$ s                                              |
| $10$ (Digits)       | $12$         | $10^{12}$                           | $10^3$ s ($\sim 16$ min)                             |
| $10$ (Digits)       | $16$         | $10^{16}$                           | $10^7$ s ($\sim 4$ months)                           |
| $10$ (Digits)       | $24$         | $10^{24}$                           | $10^{15}$ s ($\sim 32$ million years)                |
| $26$ (Lowercase)    | $8$          | $26^8 \approx 2.1 \cdot 10^{11}$    | $210$ s ($\sim 3.5$ min)                             |
| $26$ (Lowercase)    | $12$         | $26^{12} \approx 9.5 \cdot 10^{16}$ | $9.5 \cdot 10^7$ s ($\sim 3$ years)                  |
| $26$ (Lowercase)    | $16$         | $26^{16} \approx 4.4 \cdot 10^{22}$ | $4.4 \cdot 10^{13}$ s ($\sim 1.4$ million years)     |
| $26$ (Lowercase)    | $24$         | $26^{24} \approx 9.1 \cdot 10^{33}$ | $9.1 \cdot 10^{24}$ s ($\sim 3 \cdot 10^{17}$ yrs)   |
| $62$ (Alphanumeric) | $8$          | $62^8 \approx 2.2 \cdot 10^{14}$    | $2.2 \cdot 10^5$ s ($\sim 2.5$ days)                 |
| $62$ (Alphanumeric) | $12$         | $62^{12} \approx 3.2 \cdot 10^{21}$ | $3.2 \cdot 10^{12}$ s ($\sim 100,000$ years)         |
| $62$ (Alphanumeric) | $16$         | $62^{16} \approx 4.8 \cdot 10^{28}$ | $4.8 \cdot 10^{19}$ s ($\sim 1.5 \cdot 10^{12}$ yrs) |
| $62$ (Alphanumeric) | $24$         | $62^{24} \approx 1.0 \cdot 10^{43}$ | $1.0 \cdot 10^{34}$ s ($\sim 3 \cdot 10^{26}$ yrs)   |
