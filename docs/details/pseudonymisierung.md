---
head:
- - link
  - rel: stylesheet
    href: https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.5.1/katex.min.css
---

::: info
This text is provided in German as it requires coordination with our data protection officer. Given
the sensitivity of data protection matters and the importance of precise wording to ensure the
intended meaning, we are proceeding in the original language to maintain clarity and compliance.
An english version can be found [here](./pseudonymization)
:::

# Pseudonymisierung

## Anforderungen

1. Der ID-Austauschprozess erfolgt über eine Treuhandstelle (TCA).
2. Eine Rückidentifizierung durch die Treuhandstelle muss möglich sein.
3. Die sIDs müssen bei wiederholten Übertragungen konstant bleiben.

## Übertragungsprozess

In einem Übertragungsprozess fordert der CDA vom TCA die Zuordnung von oIDs zu Transport-IDs (tID)
an. Bevor das Patientenbündel an den Forschungsdomänenagenten gesendet wird, werden die oIDs durch
tIDs ersetzt.
Nach Erhalt des transportpseudonymisierten Patientenbündels fordert der RDA die Zuordnung von tIDs
zu sIDs an und ersetzt die tIDs durch die sIDs.

## Erzeugung der Transport-IDs und pseudonymisierten IDs

### sID

Der TCA verwendet gPAS zur Generierung und Speicherung von Pseudonymen.
Für jeden Patienten werden zwei Pseudonyme erzeugt:

$$
\begin{align*}
\text{patientOID} &\rightarrow \text{patientSID}\\
\textit{Salt\_} + \text{patientOID} &\rightarrow \text{Salt}
\end{align*}
$$

Als Schlüssel werden die oID des Patienten und die Konkatenation von _Salt\__ und der oID verwendet.
Hierbei ist zu beachten, dass _Salt\__ ein feststehendes Literal und keine Variable oder ein
tatsächliches Salt ist.

Das erste Pseudonym ersetzt die oID der Patientenressource, d.h. es ist ein direktes Mapping auf die
sID des Patienten, und kann zur Rückidentifizierung genutzt werden.
Das zweite Pseudonym wird als Salt für die Erzeugung der Pseudonyme für die restlichen Ressourcen
verwendet:

$$
\text{Ressourcen-sID} = \text{SHA256}(\text{Salt} + \text{oID})
$$

::: warning Sicherheitshinweis
Die Kombination aus Alphabetgröße $A$ und Salt-Länge $n$ — also $A^n$ mögliche Varianten — muss
ausreichend groß gewählt werden, um gegen Brute-Force-Angriffe resistent zu sein (siehe
Sicherheitsaspekte).
:::

### tID

Für jede oID wird eine zufällige Zahl generiert, die als tID dient.
Das Mapping

$$ \text{oID} \rightarrow \text{tID} $$

wird temporär in einem Key-Value-Store gespeichert, sodass tIDs bei erneuten Übertragungen
variieren.

## Beispiel

Angenommen, wir haben einen Patienten mit zwei Ressourcen:

```
Patient:
  oID = 1,
  Ressourcen:
  [
    Encounter: oID = 2,
    Medication: oID = 3
  ]
```

Der CDA sendet die zu pseudonymisierenden IDs an den TCA:

$$
\begin{align*}
1 &\rightarrow \text{d7dsjdg4}\\
\text{Salt\_1} &\rightarrow \text{5kf8344f}
\end{align*}
$$

### Transport-Mapping: Ersetzung der oIDs durch tIDs

Sobald der CDA die zu pseudonymisierenden oIDs an den TCA sendet, werden temporäre Transport-IDs (
tIDs) generiert.
Diese tIDs ersetzen die ursprünglichen oIDs, bevor die Daten an den RDA übermittelt werden.

**Beispiel für das Transport-Mapping:**
$$
\begin{align*}
1 &\rightarrow 84613221\\
2 &\rightarrow 34186571\\
3 &\rightarrow 97354168
\end{align*}
$$

Nach diesem Mapping wird das Bundle mit den transportpseudonymisierten IDs an den RDA
weitergeleitet:

```
transport-Patient:
  tID = 84613221,
  Ressourcen:
  [
    Encounter: tID = 34186571,
    Medication: tID = 97354168
  ]
```

### Research Mapping

Nachdem der RDA das transportpseudonymisierte Bundle erhalten hat, fordert dieser vom TCA die
Zuordnung der tIDs zu stabilen Pseudonymen (sIDs) an.
Diese sIDs sind für Forschungszwecke bestimmt und bleiben für wiederholte Übertragungen konstant.

**Beispiel für das Research-Mapping:**

$$
\begin{align*}
84613221 &\rightarrow \text{d7dsjdg4}\\
34186571 &\rightarrow \text{SHA256}(5 kf83442 )\\
97354168 &\rightarrow \text{SHA256}(5 kf83443 )
\end{align*}
$$

Anschließend ersetzt der RDA die tIDs durch die sIDs:

```
research-Patient:
 <sID = d7dsjdg4,
 Ressourcen: [
  Encounter: sID = SHA256(5kf83442),
  Medication: sID = SHA256(5kf83443)
 ]
```

## Sicherheitsaspekte

### Salt Bruteforcen

Angenommen einer Angreiferin sind die oIDs und sIDs bekannt und sie versucht eine Beziehung zwischen
oIDs und sIDs herzustellen.
Dazu versucht sie mittels Brute-Force-Angriffen das Salt zu bestimmen.

Die Dauer $T$, die die Angreiferin benötigt, um das Salt zu bestimmen, ist durch

$$
T = \frac{A^n}{v}
$$
gegeben, wobei $A$ die Alphabetgröße, $n$ die Länge des Salts, und $v$ die Anzahl der Hashes pro
Sekunde sind.

Mit heutiger Hardware sind $10^9$ Hashes pro Sekunde eine realistische Annahme.

| Alphabetgröße $(A)$   | Länge $(n)$ | Mögliche Kombinationen $(Aⁿ)$       | Zeit bei $10^9$ Hashes/Sekunde                         |
|-----------------------|-------------|-------------------------------------|--------------------------------------------------------|
| $10$ (Ziffern)        | $8$         | $10^8$                              | $0,1$ s                                                |
| $10$ (Ziffern)        | $12$        | $10^{12}$                           | $10^3$ s ($\sim 16$ min)                               |
| $10$ (Ziffern)        | $16$        | $10^{16}$                           | $10^7$ s ($\sim 4$ Monate)                             |
| $10$ (Ziffern)        | $24$        | $10^{24}$                           | $10^{15}$ s ($\sim 32$ Mio. Jahre)                     |
| $26$ (Kleinbuchst.)   | $8$         | $26^8 \approx 2,1 \cdot 10^{11}$    | $210$ s ($\sim 3,5$ min)                               |
| $26$ (Kleinbuchst.)   | $12$        | $26^{12} \approx 9,5 \cdot 10^{16}$ | $9,5 \cdot 10^7$ s ($\sim 3$ Jahre)                    |
| $26$ (Kleinbuchst.)   | $16$        | $26^{16} \approx 4,4 \cdot 10^{22}$ | $4,4 \cdot 10^{13}$ s ($\sim 1,4$ Mio. Jahre)          |
| $26$ (Kleinbuchst.)   | $24$        | $26^{24} \approx 9,1 \cdot 10^{33}$ | $9,1 \cdot 10^{24}$ s ($\sim 3 \cdot 10^{17}$ Jahre)   |
| $62$ (Alphanumerisch) | $8$         | $62^8 \approx 2,2 \cdot 10^{14}$    | $2,2 \cdot 10^5$ s ($\sim 2,5$ Tage)                   |
| $62$ (Alphanumerisch) | $12$        | $62^{12} \approx 3,2 \cdot 10^{21}$ | $3,2 \cdot 10^{12}$ s ($\sim 100.000$ Jahre)           |
| $62$ (Alphanumerisch) | $16$        | $62^{16} \approx 4,8 \cdot 10^{28}$ | $4,8 \cdot 10^{19}$ s ($\sim 1,5 \cdot 10^{12}$ Jahre) |
| $62$ (Alphanumerisch) | $24$        | $62^{24} \approx 1,0 \cdot 10^{43}$ | $1,0 \cdot 10^{34}$ s ($\sim 3 \cdot 10^{26}$ Jahre)   |
