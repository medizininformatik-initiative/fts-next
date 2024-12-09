<script setup>
import {useData} from "vitepress";
import {onMounted, ref, watch} from "vue";

onMounted(() => {
  import("rapidoc");
});

const data = useData();
const theme = ref(data.isDark.value ? "dark" : "light")

watch(data.isDark, (isDark) => {
  theme.value = isDark ? "dark" : "light";
})

const props = defineProps(['specs'])

</script>


<template>
  <div>
    <rapi-doc
        :spec-url="specs"
        show-header="false"
        show-info="false"
        allow-authentication="false"
        allow-server-selection="false"
        allow-api-list-style-selection="false"
        :primary-color="theme === 'dark' ? '#a8b1ff' : '#3451b2'"
        :bg-color="theme === 'dark' ? '#1b1b1f' : '#ffffff'"
        allow-try="false"
        regular-font="Inter, ui-sans-serif, system-ui, sans-serif"
        mono-font="ui-monospace, Menlo, Monaco, Consolas"
        :theme="theme"
        render-style="view"
        style="width: 100%; padding-right: 32px"
        schema-style="table"
    ></rapi-doc>
  </div>
</template>
