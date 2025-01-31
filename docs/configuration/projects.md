# Projects <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" /> <Badge type="warning" text="Since 5.0" />

This page documents the `projects` section of the FTSnext agent configuration file
(`application.yaml`), which is used to define the settings related to project files

_For what **project** means in this context, please see [Project Configuration](../project)_

## Configuration Example

```yaml
projects:
  directory: "./projects"
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

## Notes

* The `directory` field must be a valid relative or absolute path pointing to the desired directory.
* Ensure the specified path exists and has the necessary read/write permissions.
