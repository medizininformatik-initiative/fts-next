# Basic Authentication <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" /><Badge type="tip" text="Trust Center Agent" />

Basic authentication uses predefined username and password pairs for user access control.

## Configuration

```yaml
basic:
  users:
    - username: client
      # BCrypt hashed password for the user
      password: "{bcrypt}$2a$10$4i1TQpnBlcKOdUYO9O850.jJ8yGO8x9fQuu/l3Ki3HXgv0t9NOr4y"
      # Optionally include an unencrypted password
      password: "{noop}2mXA742aw7CGaLU6"
      # Role assigned to the user
      role: client
```

## Fields

- **`basic`**:  
  Configuration section for basic authentication.
  - **`users`** *(list)*:  
    A list of users for basic authentication.
    - **`username`** *(string)*:  
      The username for the user. Example: `client`.
    - **`password`** *(string)*:  
      The user's password. This can be in:
      - **BCrypt format**: Encrypted password for security. Example: `{bcrypt}$2a$10$...`.
      - **Noop format**: Plaintext password (use only for testing). Example: `{noop}password`.
    - **`role`** *(string)*:  
      The role assigned to the user, which defines access permissions. Example: `client`.
