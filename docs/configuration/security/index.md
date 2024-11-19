# Security <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" /><Badge type="tip" text="Trust Center Agent" /> 

The `security` configuration is used to define secure endpoint paths and authentication methods for the application.

## Configuration

```yaml
security:
  endpoints:
    # Define secured endpoint paths and their required roles
    - path: /api/v2/**
      role: client
  auth:
    # Authentication configurations
    basic:     # Basic authentication (see below)
    clientCert: # Client certificate authentication (see below)
```

## Fields

- **`security`**:  
  Root configuration section for security settings.
  - **`endpoints`** *(list)*:  
    Defines the API paths requiring security and their associated roles.
    - **`path`** *(string)*:  
      The endpoint path to secure, using glob patterns. Example: `/api/v2/**`.
    - **`role`** *(string)*:  
      The role required to access the path. Example: `client`.
  - **`auth`** *(object)*:  
    Contains the authentication mechanisms for securing endpoints.
      - **`basic`** *(object)*:  
        Configuration for [basic authentication](./basic.md).
      - **`clientCert`** *(object)*:  
        Configuration for [client certificate authentication](./client-certs.md).
