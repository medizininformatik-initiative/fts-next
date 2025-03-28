---
sidebar: true
layout: page
---

<script setup>import {withBase} from 'vitepress' </script>

<RapiDoc :specs="withBase('/tc-agent-openapi.json')" />
