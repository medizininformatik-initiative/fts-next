# HttpClientConfig

## Example

```yaml
  baseUrl: http://rd-hds:8080/fhir
  auth:
    basic:
      user: "mario"
      password: "itsame"
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

### AuthMethod

| Field Name    | Type                                                      | Required | Default | Description                      |
|---------------|-----------------------------------------------------------|----------|---------|----------------------------------|
| `basic`       | [`HttpClientBasicAuth`](#HttpClientBasicAuth)             | No       |         | Basic auth configuration.        |
| `cookieToken` | [`HttpClientCookieTokenAuth`](#HttpClientCookieTokenAuth) | No       |         | Cookie token auth configuration. |

### SSL

| Field Name | Type     | Required | Default | Description                                                                                                             |
|------------|----------|----------|---------|-------------------------------------------------------------------------------------------------------------------------|
| `bundle`   | `String` | Yes      |         | Name of the spring ssl bundle to use for ssl connection. See [Agent Configuration/SSL](../configuration/ssl-bundles.md) |

### HttpClientBasicAuth

| Field Name | Type     | Required | Default | Description         |
|------------|----------|----------|---------|---------------------|
| `user`     | `String` | Yes      |         | Basic auth username |
| `password` | `String` | Yes      |         | Basic auth password |

### HttpClientCookieTokenAuth

| Field Name | Type     | Required | Default | Description             |
|------------|----------|----------|---------|-------------------------|
| `token`    | `String` | Yes      |         | Cookie token auth token |

## References

* [RFC 7617 The 'Basic' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7617)
