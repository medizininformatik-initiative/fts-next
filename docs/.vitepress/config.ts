import {defineConfig} from 'vitepress'

export default defineConfig({
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
        items: [
          {text: "Overview", link: "/"},
        ]
      },
      {
        text: 'Usage',
        link: '/usage',
      },
      {
        text: 'Agent Configuration',
        items: [
          {text: 'Projects', link: '/configuration/projects'},
          {text: 'Runner', link: '/configuration/runner'},
          {text: 'Logging', link: '/configuration/logging'},
          {text: 'SSL', link: '/configuration/ssl-bundles'},
          {text: 'Server', link: '/configuration/server'},
          {text: 'Security', link: '/configuration/security',
            items: [
                {text: 'Basic Auth', link: '/configuration/security/basic'},
                {text: 'Client Certs', link: '/configuration/security/client-certs'},
            ]},
          {text: 'Observability', link: '/configuration/observability'},
          {text: 'De-Identification', link: '/configuration/de-identification'},
          {text: 'Consent', link: '/configuration/consent'},
        ]
      },
      {
        text: 'Project Configuration',
        items: [
          {text: 'Project', link: '/cda/project'},
        ]
      },
      {
        text: 'Development',
        link: '/development',
      },
    ],
  },
})
