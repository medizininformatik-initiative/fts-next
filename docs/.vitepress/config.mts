import {withMermaid} from 'vitepress-plugin-mermaid';

const shortVersion = (process.env.VITE_LATEST_RELEASE || "local").split(".")[0];
const currentYear = new Date().getFullYear();

export default withMermaid({
  title: "FTSnext",
  description: "SMITH FHIR Transfer Services",

  base: process.env.DOCS_BASE || "",
  lastUpdated: true,

  markdown: {
    math: true,
  },

  vue: {
    template: {
      compilerOptions: {
        isCustomElement: (tag: string) => {
          return tag.indexOf('rapi-doc') >= 0;
        }
      }
    }
  },
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
      {text: 'User Guide', link: '/usage', activeMatch: '^/(?!contributing)'},
      {text: 'Contributing', link: '/contributing/overview', activeMatch: '^/contributing'},
      {
        text: shortVersion,
        items: [
          {
            text: 'Issues',
            link: 'https://github.com/medizininformatik-initiative/fts-next/issues',
          },
          {
            text: 'Discussions',
            link: 'https://github.com/medizininformatik-initiative/fts-next/discussions',
          },
          {
            text: 'Releases',
            link: 'https://github.com/medizininformatik-initiative/fts-next/releases',
          },
        ]
      }
    ],

    sidebar: {
      '/': [
        {
          text: 'Introduction', link: '/introduction',
        },
        {
          text: 'Getting Started', link: '/usage',
          items: [
            {text: 'Prerequisites', link: '/usage/prerequisites'},
            {text: 'Installation', link: '/usage/installation'},
            {text: 'Configuration', link: '/usage/configuration'},
            {text: 'Execution', link: '/usage/execution'},
          ]
        },
        {
          text: 'Agent Configuration', link: '/agent',
          items:
              [
                {text: 'Projects', link: '/configuration/projects'},
                {text: 'Runner', link: '/configuration/runner'},
                {text: 'Logging', link: '/configuration/logging'},
                {text: 'SSL Bundles', link: '/configuration/ssl-bundles'},
                {text: 'Server', link: '/configuration/server'},
                {
                  text: 'Security', link: '/configuration/security',
                  items: [
                    {text: 'Basic Auth', link: '/configuration/security/basic'},
                    {text: 'OAuth2', link: '/configuration/security/oauth2'},
                    {text: 'Client Certs', link: '/configuration/security/client-certs'},
                  ]
                },
                {text: "OAuth2 Client", link: "/configuration/oauth2-client"},
                {text: 'Observability', link: '/configuration/observability'},
                {text: 'De-Identification', link: '/configuration/de-identification'},
                {text: 'Consent', link: '/configuration/consent'},
                {text: 'WebFlux', link: '/configuration/webflux'},
              ]
        },
        {
          text: 'Project Configuration', link: '/project',
          items:
              [
                {
                  text: 'Clinical Domain', link: '/cd-agent/project', collapsed: true,
                  items: [
                    {
                      text: 'Cohort Selector', link: '/cd-agent/cohort-selector',
                      items: [
                        {
                          text: 'trustCenterAgent',
                          link: '/cd-agent/cohort-selector/trustCenterAgent'
                        },
                        {
                          text: 'fhir',
                          link: '/cd-agent/cohort-selector/fhir'
                        },
                        {
                          text: 'external',
                          link: '/cd-agent/cohort-selector/external'
                        },

                      ]
                    },
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
              ],
        },
        {
          text: 'Technical Details', link: 'details',
          items: [
            {
              text: 'De-Identification', link: '/details/deidentification', collapsed: true,
              items: [{text: 'Pseudonymization', link: '/details/pseudonymisierung'}]
            }
          ]
        },
        {
          text: "Reference",
          items: [
            {text: 'API', link: '/open-api/cd-openapi'}
          ]
        },
      ],
      '/contributing/': [
        {
          text: 'Development', link: '/contributing/overview',
          items: [
            {text: 'Contributing', link: '/contributing/contributing'},
            {text: 'Repository Structure', link: '/contributing/structure'},
            {text: 'Clinical Domain Agent', link: '/contributing/clinical-domain-agent'},
            {
              text: 'Trust Center Agent',
              items: [
                {text: "Overview", link: '/contributing/trust-center-agent'}
              ]
            },
            {
              text: 'API',
              items: [
                {text: 'Clinical Domain Agent', link: '/contributing/open-api/cd-openapi'},
                {text: 'Trust Center Domain Agent', link: '/contributing/open-api/tc-openapi'},
                {text: 'Research Domain Agent', link: '/contributing/open-api/rd-openapi'},
              ],
            },
          ]
        },
      ]
    },

    footer: {
      message: 'Released under the <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</a>',
      copyright: `Copyright © 2024 - ${currentYear}`,
    },

    search: {
      provider: 'local'
    }
  },
})
