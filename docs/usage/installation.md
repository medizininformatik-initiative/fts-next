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

[compose]: https://docs.docker.com/compose/
