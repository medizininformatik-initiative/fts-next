---
sidebar: true
layout: page
---

<script setup>
import RapiDoc from '../components/RapiDoc.vue';
import { withBase } from 'vitepress';
</script>

<RapiDoc :specs="withBase('/cd-agent-openapi.json')" />
