# Projects <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" />

This page documents the `projects` section of the FTSnext agent configuration file
(`application.yaml`), which is used to define the settings related to project files

_For what **project** means in this context, please see [Project Configuration](../project)_

## Configuration Example

```yaml
projects:
  directory: "./projects"
```

## Fields

### `directory`

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
