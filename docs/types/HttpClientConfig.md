# HttpClientConfig <Badge type="warning" text="Since 5.0" />

## Examples

### Basic Auth

```yaml
baseUrl: http://rd-hds:8080/fhir
auth:
  basic:
    user: "mario"
    password: "itsame"
ssl:
  bundle: client
```

### OAuth2 <Badge type="warning" text="Since 5.1" />

```yaml
baseUrl: http://rd-hds:8080/fhir
auth:
  oauth2:
    registration: agent
ssl:
  bundle: client
```

## Fields

| Field Name | Type                        | Required | Default | Description                   |
|------------|-----------------------------|----------|---------|-------------------------------|
| `baseUrl`  | `String`                    | Yes      |         | Server base URL.              |
| `auth`     | [`AuthMethod`](#AuthMethod) | No       | `NONE`  | Authentication configuration. |
| `ssl`      | [`SSL`](#SSL)               | No       |         | SSL Configuration.            |

## Other Types

### AuthMethod <Badge type="warning" text="Since 5.0" />

| Field Name    | Type                                                      | Required | Default | Description                      |
|---------------|-----------------------------------------------------------|----------|---------|----------------------------------|
| `basic`       | [`HttpClientBasicAuth`](#HttpClientBasicAuth)             | No       |         | Basic auth configuration.        |
| `oauth2`      | [`HttpClientOauth2Auth`](#HttpClientOauth2Auth)           | No       |         | OAuth2 configuration.            |
| `cookieToken` | [`HttpClientCookieTokenAuth`](#HttpClientCookieTokenAuth) | No       |         | Cookie token auth configuration. |

### SSL <Badge type="warning" text="Since 5.0" />

| Field Name | Type     | Required | Default | Description                                                                                                          |
|------------|----------|----------|---------|----------------------------------------------------------------------------------------------------------------------|
| `bundle`   | `String` | Yes      |         | Name of the spring ssl bundle to use for ssl connection. See [Agent Configuration/SSL](../configuration/ssl-bundles) |

### HttpClientBasicAuth <Badge type="warning" text="Since 5.0" />

| Field Name | Type     | Required | Default | Description         |
|------------|----------|----------|---------|---------------------|
| `user`     | `String` | Yes      |         | Basic auth username |
| `password` | `String` | Yes      |         | Basic auth password |

### HttpClientOAuth2Auth <Badge type="warning" text="Since 5.1" />

| Field Name     | Type     | Required | Default | Description                                                                                                              |
|----------------|----------|----------|---------|--------------------------------------------------------------------------------------------------------------------------|
| `registration` | `String` | Yes      |         | Name of the registration defined in `application.yaml`. See [Agent Configuration/OAuth2](../configuration/oauth2-client) |

### HttpClientCookieTokenAuth <Badge type="warning" text="Since 5.0" />

| Field Name | Type     | Required | Default | Description             |
|------------|----------|----------|---------|-------------------------|
| `token`    | `String` | Yes      |         | Cookie token auth token |

## References

* [RFC 7617 The 'Basic' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7617)
* [RFC 6749 The OAuth 2.0 Authorization Framework](https://www.rfc-editor.org/rfc/rfc6749)
