import {withMermaid} from "vitepress-plugin-mermaid";

import {useSidebar} from 'vitepress-openapi'
import spec from '../open-api/openapi.json'
import cdSpec from '../open-api/cd-agent-openapi.json'
import tcSpec from '../open-api/tc-agent-openapi.json'
import rdSpec from '../open-api/rd-agent-openapi.json'

const shortVersion = (process.env.VITE_LATEST_RELEASE || "local").split(".")[0];

const sidebar = useSidebar({
  spec,
  linkPrefix: '/open-api/operations/'
})

const cdSidebar = useSidebar({
  spec: cdSpec,
  linkPrefix: '/open-api/cd-operations/'
})
const tcSidebar = useSidebar({
  spec: tcSpec,
  linkPrefix: '/open-api/tc-operations/'
})
const rdSidebar = useSidebar({
  spec: rdSpec,
  linkPrefix: '/open-api/rd-operations/'
})

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
            text: 'Releases',
            link: 'https://github.com/medizininformatik-initiative/fts-next/releases',
          },
        ]
      }
    ],

    sidebar: {
      '/': [
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
          items:
              [
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
              ],
        },
        {
          text: 'Technical Details', link: 'details',
          items: [
            {text: 'De-Identification', link: '/details/deidentification'}
          ]
        },
        {
          text: 'EndpointsTest',
          items: [
            ...sidebar.generateSidebarGroups(),

          ],
        },
        {
          text: 'Endpoints',
          items: [{
            text: 'Clinical Domain Agent', collapsed: true, items: [
              ...cdSidebar.generateSidebarGroups(),
            ]
          }, {
            text: 'Trust Center Domain Agent', collapsed: true, items: [
              ...tcSidebar.generateSidebarGroups(),
            ]
          }, {
            text: 'Research Domain Agent', collapsed: true, items: [
              ...rdSidebar.generateSidebarGroups(),
            ]
          },
          ],
        },
      ],
      '/contributing/': [
        {
          text: 'Development', link: '/contributing/overview',
          items: [
            {text: 'Repository Structure', link: '/contributing/structure'},
            {text: 'Clinical Domain Agent', link: '/contributing/clinical-domain-agent'},
            {text: 'Trust Center Agent', link: '/contributing/trust-center-agent'},
          ]
        },
      ]
    },

    footer: {
      message: 'Released under the <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</a>',
      copyright: 'Copyright 2024',
    },

    search: {
      provider: 'local'
    }
  },
})
