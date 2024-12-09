import {theme, useOpenapi} from 'vitepress-openapi'
import DefaultTheme from 'vitepress/theme'
import spec from '../../open-api/openapi.json'
import cdSpec from '../../open-api/cd-agent-openapi.json'
import tcSpec from '../../open-api/tc-agent-openapi.json'
import rdSpec from '../../open-api/rd-agent-openapi.json'
import 'vitepress-openapi/dist/style.css'

export default {
  extends: DefaultTheme,
  enhanceApp({app}) {
    theme.enhanceApp({
      app, openapi: useOpenapi({
        spec
      })
    })

    theme.enhanceApp({
      app, openapi: useOpenapi({
        spec: cdSpec
      })
    })
    theme.enhanceApp({
      app, openapi: useOpenapi({
        spec: tcSpec
      })
    })
    theme.enhanceApp({
      app, openapi: useOpenapi({
        spec: rdSpec
      })
    })
  },
}
