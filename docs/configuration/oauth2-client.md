# OAuth2 Client Configuration

If an agent is supposed to authenticate at an endpoint of  (e.g. gics, rd-agent) via OAuth2 one must
register one or more
clients.
Then the registration may be referenced by its `registrationId`
in [HttpClientConfig](../types/HttpClientConfig).

See [Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/core.html#oauth2Client-client-registration)
for more details on how to configure OAuth2 clients.

## Configuration Example

```yaml
spring.security.oauth2.client:
  registration:
    agent:
      authorization-grant-type: client_credentials
      client-id: cd-client
      client-secret: tIQfOvBuhyR1dw9OQ3E4tCeTvcHtiW84
      provider: keycloak
  provider:
    keycloak:
      issuer-uri: http://localhost:8080/realms/fts
```
