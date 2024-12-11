# OAuth2 Authentication <Badge type="tip" text="All Agents" />

Allow access from clients authenticated by the given authority (`issuer`).

## Configuration Example

```yaml
oauth2:
  issuer: http://localhost:8080/realms/fts
```

## Fields

### `issuer`

* **Desciption**: URL of the authorization server
* **Type**: URL
* **Example**: `http://localhost:8080/realms/fts`
