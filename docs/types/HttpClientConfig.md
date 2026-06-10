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

| Field Name  | Type                          | Required | Default                | Description                   |
|-------------|-------------------------------|----------|------------------------|-------------------------------|
| `baseUrl`   | `String`                      | Yes      |                        | Server base URL.              |
| `auth`      | [`AuthMethod`](#authmethod)   | No       | `NONE`                 | Authentication configuration. |
| `ssl`       | [`SSL`](#ssl)                 | No       |                        | SSL Configuration.            |
| `redirects` | [`Redirects`](#redirects)     | No       | `FOLLOW_SAFE`          | Redirect-following policy.    |

## Other Types

### Redirects <Badge type="warning" text="Since 5.7" />

Controls whether HTTP redirects (3xx) are followed for this connection.

| Value           | Behaviour                                                                                                                                                                            |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `FOLLOW_SAFE`   | Follow redirects (e.g. an HDS behind a reverse proxy answering `307`), but refuse HTTPS&rarr;HTTP downgrades. **Default.**                                                          |
| `ALWAYS_FOLLOW` | Follow all redirects, **including** HTTPS&rarr;HTTP downgrades. A downgrade exposes credentials and data over plaintext, so only use this for a trusted cross-scheme upstream.      |
| `DONT_FOLLOW`   | Do not follow redirects.                                                                                                                                                            |

A redirect that is **not** followed (every 3xx under `DONT_FOLLOW`, or an HTTPS&rarr;HTTP
downgrade / unfollowed `POST` `307`/`308` under the follow modes) is turned into an **error**
instead of passing through as an empty-bodied success — the failure mode that otherwise produces a
silently empty transfer. Because a redirect is deterministic, it is treated as terminal and is
**not retried**. Use `DONT_FOLLOW` to fail fast and force a misconfigured upstream `baseUrl` to be
corrected so the server returns `200` directly.

### AuthMethod <Badge type="warning" text="Since 5.0" />

| Field Name    | Type                                                      | Required | Default | Description                      |
|---------------|-----------------------------------------------------------|----------|---------|----------------------------------|
| `basic`       | [`HttpClientBasicAuth`](#httpclientbasicauth)             | No       |         | Basic auth configuration.        |
| `oauth2`      | [`HttpClientOauth2Auth`](#httpclientoauth2auth)           | No       |         | OAuth2 configuration.            |
| `cookieToken` | [`HttpClientCookieTokenAuth`](#httpclientcookietokenauth) | No       |         | Cookie token auth configuration. |

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
