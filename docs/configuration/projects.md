# Projects <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" /> <Badge type="warning" text="Since 5.0" />

This page documents the `projects` section of the FTSnext agent configuration file
(`application.yaml`), which is used to define the settings related to project files

_For what **project** means in this context, please see [Project Configuration](../project)_

## Configuration Example

```yaml
projects:
  directory: "./projects"
  strict-validation: false
```

## Fields

### `directory` <Badge type="warning" text="Since 5.0" />

* **Description**: Specifies the directory path where the project files are stored.
* **Type**: String
* **Default**: `"./projects"`
* **Example**:
  ```yaml
  projects:
    directory: "./my-custom-projects"
  ```

### `strict-validation` <Badge type="warning" text="Since 5.7" />

* **Description**: Controls whether the agent fails to start when a project configuration
  is invalid or contains unknown keys. When set to `false` (default), invalid projects are
  skipped and logged; the agent starts with the remaining valid projects. When set to `true`,
  any invalid project configuration causes the agent to abort startup immediately.
* **Type**: Boolean
* **Default**: `false`
* **Example**:
  ```yaml
  projects:
    strict-validation: true
  ```

## Notes

* The `directory` field must be a valid relative or absolute path pointing to the desired directory.
* Ensure the specified path exists and has the necessary read/write permissions.
* With `strict-validation: false`, invalid projects are silently skipped. Check the agent logs
  for `ERROR` messages if a project does not appear in the running configuration.
* With `strict-validation: true`, the agent will fail to start with a `ProjectConfigurationException`
  if any project file is unreadable, unparseable, contains unknown configuration keys, or fails
  step instantiation. This is recommended for production deployments and especially initial setups
  to catch configuration errors early.
