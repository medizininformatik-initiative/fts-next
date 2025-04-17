<script setup>
import {defineProps, onMounted, ref} from 'vue'

const props = defineProps({
  input: {
    type: String,
    required: true
  }
})

const json = ref(null)

onMounted(async () => {
  const response = await fetch(props.input)
  json.value = await response.json()
})
</script>

<template>
  <div v-if="json">
    <h3>{{ json.title || json.name }}</h3>
    <p><strong>URL:</strong> {{ json.url }}</p>
    <p><strong>Version:</strong> <code>{{ json.version }}</code></p>
    <p><strong>Status:</strong> <code>{{ json.status }}</code></p>
    <p><strong>Description:</strong> <code>{{ json.description }}</code></p>

    <h4>Concepts</h4>
    <table>
      <thead>
      <tr>
        <th>Code</th>
        <th>Display</th>
        <th>Definition</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="(concept, index) in json.concept" :key="index">
        <td><code>{{ concept.code }}</code></td>
        <td>{{ concept.display }}</td>
        <td>{{ concept.definition || '—' }}</td>
      </tr>
      </tbody>
    </table>
  </div>
  <div v-else>
    <p>Loading CodeSystem...</p>
  </div>
</template>
