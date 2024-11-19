# Projects <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" />

This document describes the configuration options available for managing project settings in the
`projects` section of the configuration file. A project in the scope of clinical and research domain
agent contains configuration parameters for a data transfer

Also See
* [cda/projects](cd-agent/projects)
* [rda/projects](rd-agent/projects)

## Configuration Structure

The `projects` section is used to define the settings related to project files. Below is the
structure and explanation of the available options:

```yaml
projects:
  # Directory where the project files are located
  directory: "./projects"
```

### Fields

#### `directory`

- **Description**: Specifies the directory path where the project files are stored.
- **Type**: String
- **Default**: `"./projects"`
- **Example**:
  ```yaml
  projects:
    directory: "./my-custom-projects"
  ```

### Notes

- The `directory` field must be a valid relative or absolute path pointing to the desired directory.
- Ensure the specified path exists and has the necessary read/write permissions.

This simple configuration allows you to customize the location of your project files based on your
requirements.
