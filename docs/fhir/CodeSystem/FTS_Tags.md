<script setup>
import CodeSystemViewer from '../../components/CodeSystemViewer.vue';
import {withBase} from "vitepress";
</script>

# FTS Tagging of FHIR Resources

As part of its processing workflow FTS adds a specific tag to every resource it modifies.

# Tag Format

<CodeSystemViewer :input="withBase('/CodeSystem-fts-code-system.json')"/>
