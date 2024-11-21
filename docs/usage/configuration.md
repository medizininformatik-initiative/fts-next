# Configuration

The configuration is split in two parts, one for the agents  
and one the transfer processes.

## Agent

The agents are configured via their `application.yml` files.
Please consult the [agent configuration](configuration)
for a detailed description.

## Transfer Project

The transfer processes are represented by projects.
The project configuration files are placed in the path set by
`projects.directory` in the agents' `application.yml`.
Note that only CDA and RDA have projects.

The `projects/example.yml` in the cd-agent and rd-agent templates
show the settings for an exemplary transfer project.
