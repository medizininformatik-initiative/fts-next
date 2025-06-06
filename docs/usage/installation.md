---
aside: false
---

<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const download = 'https://github.com/medizininformatik-initiative/fts-next/releases/download/' + release;
</script>

# Installation

The FTSnext setup consists of three agents that must be deployed.

## Templates

For each agent, we provide an archive with all the necessary files and directories to run it
using [Docker Compose][compose].

<table class="downloads">
<thead>
  <tr>
    <th>Clinical Domain Agent</th>
    <th>Trust Center Agent</th>
    <th>Research Domain Agent</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td>
      <a :href="download + '/cd-agent.tar.gz'">cd-agent.tar.gz</a><br>
      <small>
        <a :href="download + '/cd-agent.tar.gz.sha256'">Checksum</a> ·
        <a :href="download + '/cd-agent.tar.gz.intoto.jsonl'">Provenance</a>
      </small>
    </td>
    <td>
      <a :href="download + '/tc-agent.tar.gz'">tc-agent.tar.gz</a><br>
      <small>
        <a :href="download + '/tc-agent.tar.gz.sha256'">Checksum</a> ·
        <a :href="download + '/tc-agent.tar.gz.intoto.jsonl'">Provenance</a>
      </small>
    </td>
    <td>
      <a :href="download + '/rd-agent.tar.gz'">rd-agent.tar.gz</a><br>
      <small>
        <a :href="download + '/rd-agent.tar.gz.sha256'">Checksum</a> ·
        <a :href="download + '/rd-agent.tar.gz.intoto.jsonl'">Provenance</a>
      </small>
    </td>
  </tr>
</tbody>
</table>

For example use `wget` and `tar` to download and unpack the agent template:

::: code-group

```shell-vue [CD Agent]
wget {{ download }}/cd-agent.tar.gz
tar -xvf cd-agent.tar.gz
```

```shell-vue [TC Agent]
wget {{ download }}/tc-agent.tar.gz
tar -xvf tc-agent.tar.gz
```

```shell-vue [RD Agent]
wget {{ download }}/rd-agent.tar.gz
tar -xvf rd-agent.tar.gz
```

:::

It will provide the following directory structure:

::: code-group

```shell [CD Agent]
cd-agent/
├── application.yaml                    # server-related configurations, such as SSL, file paths, etc.
├── compose.yaml                        # container image reference, network settings, healthcheck
└── projects/                           # project configuration directory
    ├── external-consent-example.yaml   # example project configuration with external consent  
    ├── fhir-consent-example.yaml       # example project configuration with consent from FHIR server
    ├── gics-consent-example.yaml       # example project configuration with consent from gICS
    └── example/                        # other files needed for the example project to function
        └── deidentifhir/               # deidentifhir configuration used in the example project
```

```shell [TC Agent]
tc-agent/
├── application.yaml       # server-related configurations, such as SSL, file paths, etc.
└── compose.yaml           # container image reference, network settings, healthcheck



# Trust Center Agent has no projects
```

```shell [RD Agent]
rd-agent/
├── application.yaml       # server-related configurations, such as SSL, file paths, etc.
└── compose.yaml           # container image reference, network settings, healthcheck
└── projects/              # project configuration directory
    ├── example.yaml       # example project configuration  
    └── example/           # other files needed for the example project to function
        └── deidentifhir/  # deidentifhir configuration used in the example project
```

:::

## Verification <Badge type="warning" text="Since 5.3" />

To ensure trust and security in the software supply chain, verification of release artifacts
confirms that the downloaded files are authentic, unaltered, and originate from the intended source.
This process helps to protect users from tampered or malicious builds by using cryptographic proofs
of provenance. Each release uses [SLSA](https://slsa.dev) and [cosign][cosign] to generate
provenance metadata for both release archives and container images. These artifacts are signed
during the build process, enabling independent verification of their origin and integrity.

For container images, we use [cosign][cosign] also to sign images and attach SBOMs, stored alongside
the image in the container registry. This allows users to confirm the image was built by the
expected CI
pipeline and has not been modified after publication.

Release archives include SLSA provenance files that can be verified using the slsa-verifier tool.
Together, these mechanisms provide a robust foundation for validating downloaded components.

### Release Archives

Each agent archive is accompanied by a provenance file in [SLSA](https://slsa.dev) format, which
verifies the authenticity and integrity of the downloaded files. The `slsa-verifier` tool checks
that the artifact was built from the correct source and tag. <sup>[1](#slsa-verifier)</sup>

::: code-group

```shell-vue [CD Agent]
wget {{ download }}/cd-agent.tar.gz.intoto.jsonl
slsa-verifier verify-artifact cd-agent.tar.gz \
  --source-uri github.com/medizininformatik-initiative/fts-next \
  --source-tag {{ release }} \
  --provenance-path cd-agent.tar.gz.intoto.jsonl
```

```shell-vue [TC Agent]
wget {{ download }}/tc-agent.tar.gz.intoto.jsonl
slsa-verifier verify-artifact tc-agent.tar.gz \
  --source-uri github.com/medizininformatik-initiative/fts-next \
  --source-tag {{ release }} \
  --provenance-path tc-agent.tar.gz.intoto.jsonl
```

```shell-vue [RD Agent]
wget {{ download }}/rd-agent.tar.gz.intoto.jsonl
slsa-verifier verify-artifact rd-agent.tar.gz \
  --source-uri github.com/medizininformatik-initiative/fts-next \
  --source-tag {{ release }} \
  --provenance-path rd-agent.tar.gz.intoto.jsonl
```

:::

If the verification passes the output should contain confirmation that the artifact is valid
and matches the signed provenance:

```
PASSED: SLSA verification passed
```

---

<small id="slsa-verifier">
  1. Please see official <code>slsa-verifier</code> <a href="https://github.com/slsa-framework/slsa-verifier#installation">installation</a> instructions
</small>

### Container Images

Each release archive includes a verify.sh utility script for verifying the provenance of the
container images used in the corresponding compose files, by running `./verify.sh`. Use
`./verify.sh {{ release }}`, if the image versions in the docker compose file have been updated
since
first installation. The verify script uses [slsa-verifier][slsa-verifier] and [cosign][cosign-cli]
to check whether the image was built by the FTSnext GitHub actions.

**Example output:**

```-vue
$ ./verify.sh {{ release }}
# Verifying image provenance, assert image was built 
- from github.com/medizininformatik-initiative/fts-next 
- for tag {{ release }}
* Using slsa-verifier ✔ 
* Using cosign ✔ 
```

[compose]: https://docs.docker.com/compose/

[cosign]: https://docs.sigstore.dev/cosign/signing/overview/

[slsa-verifier]: https://github.com/slsa-framework/slsa-verifier#installation

[cosign-cli]: https://docs.sigstore.dev/cosign/system_config/installation/

<style>
table.downloads {
  display: table;
  width: 90%;
  margin: 0 auto;
}

table.downloads td,
table.downloads th {
  text-align: center;
}

table.downloads small a {
  text-decoration: none;
}
</style>
