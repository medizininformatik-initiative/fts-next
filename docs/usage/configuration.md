<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
</script>

# Configuration

This documentation is generated for the main branch of FTSnext, it may document features that are
not yet released. Therefor configuration options are annotated with a badge to indicate in which
version the feature is or will be available, e.g. <Badge type="warning" text="Since 5.1" />

The latest stable version
is: [{{ release }}](https://github.com/medizininformatik-initiative/fts-next/releases/latest)

## Agent Configuration

Agent configuration in the context of FTSnext means the configuration of the server components,
like ports, certificates, logging, etc. Please see the [Agent Configuration](../agent) section for
more information.

_Note: For CDA and RDA you can mainly use the provided `application.yaml` as-is. For the TCA at
least deidentification and consent backends must be configured in the `application.yaml`_
We highly recommend configuring https using the [ssl-bundles](../configuration/ssl-bundles) and
[server](../configuration/server) sections and have a look at the
[security](../configuration/security) section for authentication/authorization.

## Transfer Project Configuration

The transfer processes are represented by projects. The project configuration files are placed in
the path set by `projects.directory` in the agents' `application.yaml`. Note that only CDA and RDA
have projects.

The `example.yaml` files in `projects` in the cd-agent and rd-agent templates show the settings for 
an exemplary transfer project.
**This cannot be used as-is, as hostnames and ports differ from dic to dic**,
_unless you run FTSnext on a single machine using docker compose only,
which we would only recommend for development or testing purposes._
