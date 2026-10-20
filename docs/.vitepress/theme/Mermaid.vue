<script setup lang="ts">
import {onMounted, ref, useId, watch} from 'vue';
import {useData} from 'vitepress';

const props = defineProps<{ code: string }>();
const {isDark} = useData();
const id = `mermaid${useId()}`.replace(/[^\w-]/g, '');
const svg = ref('');

async function render() {
  const {default: mermaid} = await import('mermaid');
  mermaid.initialize({startOnLoad: false, theme: isDark.value ? 'dark' : 'default'});
  svg.value = (await mermaid.render(id, decodeURIComponent(props.code))).svg;
}

onMounted(render);
watch(isDark, render);
</script>

<template>
  <div class="mermaid" v-html="svg"></div>
</template>
