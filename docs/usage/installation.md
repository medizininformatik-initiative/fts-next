---
aside: false
---

# Installation

The FTSnext setup consists of three agents that must be deployed.

### Templates

For each agent, we offer a template docker setup for download.

| Agent |        Download Link        |
|:------|:---------------------------:|
| CDA   | [cd-agent.tar.gz][cd-agent] |
| TCA   | [tc-agent.tar.gz][tc-agent] |
| RDA   | [rd-agent.tar.gz][rd-agent] |

[cd-agent]: https://github.com/medizininformatik-initiative/fts-next/releases/download/v5.0.0/cd-agent.tar.gz

[tc-agent]: https://github.com/medizininformatik-initiative/fts-next/releases/download/v5.0.0/tc-agent.tar.gz

[rd-agent]: https://github.com/medizininformatik-initiative/fts-next/releases/download/v5.0.0/rd-agent.tar.gz

For example, to download and unpack the cd-agent template:

```shell
wget https://github.com/medizininformatik-initiative/fts-next/releases/download/v5.0.0/cd-agent.tar.gz
tar -xvf cd-agent.tar.gz
```

It will provide the following directory structure:

```shell
cd-agent/
├── application.yml        # server-related configurations, such as SSL, file paths, etc.
├── compose.yml            # container image reference, network settings, healthcheck
└── projects/              # project configuration directory
    ├── example.yml        # example project configuration  
    └── example/           # other files needed for the example project to function
        └── deidentifhir/  # deidentifhir configuration used in the example project
```
