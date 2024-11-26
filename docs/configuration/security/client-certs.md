# Client Certificate Authentication <Badge type="tip" text="All Agents" />

Client certificate authentication validates users based on their SSL/TLS certificates and associates
the extract cn ([username](#username)) with an application [role](#role).

#### Configuration Example

```yaml
clientCert:
  users:
  - username: default
    role: client
```

#### Fields

### `users` _(list)_

* #### `username`
  * **Description**: Username for the user, which is extracted from the client certificate.
  * **Type**: String
  * **Example**: `client`

* #### `role`
  * **Description**: R assigned to the user, which defines access permissions.
    R are referenced in the [endpoints list](../security#endpoints-list)
  * **Type**: String
  * **Example**: `client`
