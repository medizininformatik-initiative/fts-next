# Server <Badge type="tip" text="All Agents" />

This page documents the `server` section of the FTSnext agent configuration file
(`application.yaml`). 

## Configuration Example

```yaml
server:
  ssl:
    # Use the server SSL bundle configuration defined above
    bundle: server
    # Specify client authentication requirement: 'want' means optional
    client-auth: want
    # Specify client authentication requirement: 'need' means required
    client-auth: need
```

## Fields

### `ssl`

This section manages the SSL configuration of the server. SSL ensures secure communication between
the client and the server by encrypting data.

* #### `bundle`
  * **Description**: Specifies the SSL bundle to use.
  * **Type**: String
  * **Default**: `server`
  * **Usage**: The name of the SSL bundle, which contains the SSL certificates and key
    configurations.
  * See [ssl-bundles](./ssl-bundles) for declaring ssl bundles

* #### `client-auth`
  * **Description**: Defines the client authentication requirement for SSL communication.
  * **Type**: String
  * **Allowed Values**:
    - `want`: Client authentication is optional. The server will request a client certificate,
      but it will proceed even if the client does not provide one.
    - `need`: Client authentication is mandatory. The server will terminate the connection if
      the client does not provide a valid certificate.
  * **Default**: `none` (if not specified, no client authentication is required).

## Notes

* **SSL Configuration**:
  * Ensure that the SSL bundle contains all necessary files, such as the server certificate,
    private key, and CA certificate.
  * Misconfiguration may lead to connection failures or weakened security.

* **Client Authentication**:
  * `want` is suitable for environments where backward compatibility with unauthenticated clients
    is needed.
  * `need` is recommended for high-security environments to ensure only trusted clients can
    establish a connection.

* **Default Behavior**:
  * If the `ssl` block is commented out or omitted, SSL is not enabled, and communication occurs
    over plain text.

## References

* **Spring Boot Documentation**:
  * [Spring Boot Security Reference](https://docs.spring.io/spring-security/site/docs/current/reference/html5/)
  * [Securing Spring Boot Applications With SSL](https://spring.io/blog/2023/06/07/securing-spring-boot-applications-with-ssl)
  * [Embedded Web Servers/Configure SSL](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.configure-ssl)

* **SSL and TLS Concepts**:
  * [What is SSL?](https://www.ssl.com/faqs/what-is-ssl/)
  * [TLS vs. SSL](https://www.cloudflare.com/learning/ssl/what-is-ssl/)

* **Best Practices**:
  * [OWASP: Transport Layer Protection Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Protection_Cheat_Sheet.html)
