# Client Certificate Authentication <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" /><Badge type="tip" text="Trust Center Agent" />

Client certificate authentication validates users based on their SSL/TLS certificates.

#### Configuration

```yaml
clientCert:
  users:
    - username: default
      # Role assigned to the user
      role: client
```

---

#### Fields

- **`clientCert`**:  
  Configuration section for client certificate authentication.

    - **`users`** *(list)*:  
      A list of users authenticated via client certificates.
        - **`username`** *(string)*:  
          The username associated with the client certificate. Example: `default`.
        - **`role`** *(string)*:  
          The role assigned to the user for access control. Example: `client`.
