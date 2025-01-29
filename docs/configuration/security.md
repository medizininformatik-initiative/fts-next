# Security <Badge type="tip" text="All Agents" />

The `security` configuration is used to define secure endpoint paths and authentication methods for
FTSnext agents.

## Configuration Example

```yaml
security:
  endpoints:
  - path: /api/v2/**
    role: client
  auth:
    basic: [ ... ]
    clientCert: [ ... ]
```

## Fields

### `endpoints` _(list)_

Defines the API paths requiring security and their associated roles.

* #### `path`
  * **Description**: The endpoint path to secure, using glob patterns
  * **Type**: String
  * **Example**: `/api/v2/**`

* #### `role` *(string)*
  * **Description**: The role required to access the path
  * **Type**: String
  * **Example**: `client`

### `auth`

Contains the authentication mechanisms for securing endpoints.

* #### `basic`
  Configuration for [basic authentication](./security/basic).
* #### `oauth2`
  Configuration for [oauth2 authentication](./security/oauth2).
* #### `clientCert`
  Configuration for [client certificate authentication](./security/client-certs).
