import {withMermaid} from "vitepress-plugin-mermaid";


export default withMermaid({
  title: "FTSnext",
  description: "SMITH FHIR Transfer Services",

  base: process.env.DOCS_BASE || "",
  lastUpdated: true,

  themeConfig: {
    outline: false,

    editLink: {
      pattern: 'https://github.com/medizininformatik-initiative/fts-next/edit/main/docs/:path',
      text: 'Edit'
    },

    socialLinks: [
      {icon: 'github', link: 'https://github.com/medizininformatik-initiative/fts-next'}
    ],

    nav: [
      {text: 'Home', link: '/'},
      {
        text: "v5.0.0-SNAPSHOT",
        items: [
          {
            text: 'Issues',
            link: 'https://github.com/medizininformatik-initiative/fts-next/issues',
          },
          {
            text: 'Releases',
            link: 'https://github.com/medizininformatik-initiative/fts-next/releases',
          },
        ]
      }
    ],

    sidebar: [
      {
        text: 'Usage', link: '/usage',
        items: [
          {text: 'Prerequisites', link: '/usage/prerequisites'},
          {text: 'Installation', link: '/usage/installation'},
          {text: 'Configuration', link: '/usage/configuration'},
          {text: 'Execution', link: '/usage/execution'},
        ]
      },
      {
        text: 'Agent Configuration', link: '/configuration',
        items: [
          {text: 'Projects', link: '/configuration/projects'},
          {text: 'Runner', link: '/configuration/runner'},
          {text: 'Logging', link: '/configuration/logging'},
          {text: 'SSL', link: '/configuration/ssl-bundles'},
          {text: 'Server', link: '/configuration/server'},
          {
            text: 'Security', link: '/configuration/security',
            items: [
              {text: 'Basic Auth', link: '/configuration/security/basic'},
              {text: 'Client Certs', link: '/configuration/security/client-certs'},
            ]
          },
          {text: 'Observability', link: '/configuration/observability'},
          {text: 'De-Identification', link: '/configuration/de-identification'},
          {text: 'Consent', link: '/configuration/consent'},
        ]
      },
      {
        text: 'Project Configuration', link: '/project',
        items: [
          {
            text: 'Clinical Domain', link: '/cd-agent/project', collapsed: true,
            items: [
              {text: 'Cohort Selector', link: '/cd-agent/cohort-selector'},
              {text: 'Data Selector', link: '/cd-agent/data-selector'},
              {text: 'Deidentificator', link: '/cd-agent/deidentificator'},
              {text: 'Bundle Sender', link: '/cd-agent/bundle-sender'},
            ]
          },
          {
            text: 'Research Domain', link: '/rd-agent/project', collapsed: true,
            items: [
              {text: 'Deidentificator', link: '/rd-agent/deidentificator'},
              {text: 'Bundle Sender', link: '/rd-agent/bundle-sender'},
            ]
          },
        ]
      },
      {
        text: 'Development',
        link: '/development',
      },
      {
        text: 'Technical Details', link: 'technical-details',
        items: [
          {text: 'De-Identification', link: '/technical-details/deidentification'}
        ]
      }
    ],

    footer: {
      message: 'Released under the <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</a>',
      copyright: 'Copyright 2024',
    },

    search: {
      provider: 'local'
    }
  },
})
