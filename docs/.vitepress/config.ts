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
        text: 'Development',
        link: '/development',
      },
    ],
  },
})
