import {defineConfig} from 'vitepress'

export default defineConfig({
  title: "FTSnext",
  description: "SMITH FHIR Transfer Services",

  base: process.env.DOCS_BASE || "",
  lastUpdated: true,

  themeConfig: {
    outline: false,

    editLink: {
      pattern: 'https://github.com/life-research/fts-next/edit/master/docs/:path',
      text: 'Edit this page on GitHub'
    },

    socialLinks: [
      {icon: 'github', link: 'https://github.com/life-research/fts-next'}
    ],

    nav: [
      {text: 'Home', link: '/'},
      {
        text: "v5.0.0-SNAPSHOT",
        items: [
          {
            text: 'Issues',
            link: 'https://github.com/life-research/fts-next/issues',
          },
          {
            text: 'Releases',
            link: 'https://github.com/life-research/fts-next/releases',
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
