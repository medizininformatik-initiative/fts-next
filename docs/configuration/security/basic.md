# Basic Authentication <Badge type="tip" text="All Agents" /> <Badge type="warning" text="Since 5.0" />

Basic authentication uses predefined username and password pairs for user access control.

## Configuration Example

```yaml
basic:
  users:
  - username: client
    password: "{bcrypt}$2a$10$4i1TQpnBlcKOdUYO9O850.jJ8yGO8x9fQuu/l3Ki3HXgv0t9NOr4y"
    password: "{noop}2mXA742aw7CGaLU6"
    role: client
```

## Fields

### `users` _(list)_

* #### `username` <Badge type="warning" text="Since 5.0" />
  * **Description**: The username for the user
  * **Type**: String
  * **Example**: `client`

* #### `password` <Badge type="warning" text="Since 5.0" />
  * **Description**: The user's password
  * **Type**: String
  * **Examples**:
    * **BCrypt format**: Encrypted password for security. Example: `{bcrypt}$2a$10$...`.
    * **Noop format**: Plaintext password (use only for testing). Example: `{noop}password`.

* #### `role` <Badge type="warning" text="Since 5.0" />
  * **Description**: The role assigned to the user, which defines access permissions.
    The roles are referenced in the [endpoints list](../security#endpoints-list)
  * **Type**: String
  * **Example**: `client`
