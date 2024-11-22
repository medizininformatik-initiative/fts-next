---
aside: false
---

# Installation

The FTSnext setup consists of three agents that must be deployed.

### Templates

For each agent, we offer a template docker setup for download.

| Agent |      Download Link       |
|:------|:------------------------:|
| CDA   | [cd-agent.zip][cd-agent] |
| TCA   | [tc-agent.zip][tc-agent] |
| RDA   | [rd-agent.zip][rd-agent] |

[cd-agent]: https://github.com/medizininformatik-initiative/fts-next/releases/download/v5.0.0/cd-agent.zip

[tc-agent]: https://github.com/medizininformatik-initiative/fts-next/releases/download/v5.0.0/tc-agent.zip

[rd-agent]: https://github.com/medizininformatik-initiative/fts-next/releases/download/v5.0.0/rd-agent.zip

For example, to download and unpack the cd-agent template:

```shell
wget https://github.com/medizininformatik-initiative/fts-next/releases/download/v5.0.0/cd-agent.zip
unzip cd-agent-template.zip
```

It will provide the following directory structure:

```shell
cd-agent/
├── application.yml
├── compose.yml
└── projects
├── example
│   └── deidentifhir
└── example.yml
```

In the `application.yml` are server related settings e.g. SSL certificates or the path to the
project setting files.  
The `projects/example.yml` shows the settings for an exemplary transfer project.
