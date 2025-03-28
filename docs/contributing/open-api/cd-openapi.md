---
sidebar: true
layout: page
---

<script setup>import {withBase} from 'vitepress' </script>

<RapiDoc :specs="withBase('/cd-agent-openapi.json')" />
