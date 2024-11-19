# Basic Authentication <Badge type="tip" text="All Agents" />

Basic authentication uses predefined username and password pairs for user access control.

## Configuration

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

* #### `username`
  * **Description**: The username for the user
  * **Type**: String
  * **Example**: `client`

* #### `password`
  * **Description**: The user's password
  * **Type**: String
  * **Examples**:
    * **BCrypt format**: Encrypted password for security. Example: `{bcrypt}$2a$10$...`.
    * **Noop format**: Plaintext password (use only for testing). Example: `{noop}password`.

* #### `role`
  * **Description**: The role assigned to the user, which defines access permissions.
    The roles are referenced in the [endpoints list](./index.md#endpoints-list)
  * **Type**: String
  * **Example**: `client`
