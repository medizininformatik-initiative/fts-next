---
aside: false
---

<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const download = 'https://github.com/medizininformatik-initiative/fts-next/releases/download/' + release;
</script>

# Installation

The FTSnext setup consists of three agents that must be deployed.

### Templates

For each agent, we offer an archive containing necessary files and directories for running using
docker [compose][compose].

|                   Clinical Domain Agent                    |                     Trust Center Agent                     |                   Research Domain Agent                    |
|:----------------------------------------------------------:|:----------------------------------------------------------:|:----------------------------------------------------------:|
| <a :href="download+'/cd-agent.tar.gz'">cd-agent.tar.gz</a> | <a :href="download+'/tc-agent.tar.gz'">tc-agent.tar.gz</a> | <a :href="download+'/rd-agent.tar.gz'">rd-agent.tar.gz</a> |

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
├── application.yaml       # server-related configurations, such as SSL, file paths, etc.
├── compose.yaml           # container image reference, network settings, healthcheck
└── projects/              # project configuration directory
    ├── example.yaml       # example project configuration  
    └── example/           # other files needed for the example project to function
        └── deidentifhir/  # deidentifhir configuration used in the example project
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
of provenance.

For container images, we use [cosign][cosign] to sign images and attach SBOMs, stored alongside the
image in the container registry. This allows users to confirm the image was built by the expected CI
pipeline
and has not been modified after publication.

### Container Images

Each release archive includes a verify.sh utility script for verifying the provenance of the
container images used in the corresponding compose files, by running `./verify.sh`. Use
`./verify.sh {{ release }}` if he image versions in the docker compose file have been updated since
first installation. The verify script uses [cosign][cosign-cli] to check whether the image was built
by the FTSnext GitHub actions.

**Example output:**

```-vue
$ ./verify.sh {{ release }}
# Verifying image provenance, assert image was built 
- from github.com/medizininformatik-initiative/fts-next 
- for tag {{ release }}
* Using cosign ✔ 
```

[compose]: https://docs.docker.com/compose/

[cosign]: https://docs.sigstore.dev/cosign/signing/overview/

[cosign-cli]: https://docs.sigstore.dev/cosign/system_config/installation/