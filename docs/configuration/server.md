# Server <Badge type="tip" text="All Agents" /> <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.0" />

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
  # If running behind a reverse proxy
  forward-headers-strategy: framework
```

## Fields

### `ssl` <Badge type="warning" text="Since 5.0" />

This section manages the SSL configuration of the server. SSL ensures secure communication between
the client and the server by encrypting data.

* #### `bundle` <Badge type="warning" text="Since 5.0" />
  * **Description**: Specifies the SSL bundle to use.
  * **Type**: String
  * **Default**: `server`
  * **Usage**: The name of the SSL bundle, which contains the SSL certificates and key
    configurations.
  * See [ssl-bundles](./ssl-bundles) for declaring ssl bundles

* #### `client-auth` <Badge type="warning" text="Since 5.0" />
  * **Description**: Defines the client authentication requirement for SSL communication.
  * **Type**: String
  * **Allowed Values**:
    - `want`: Client authentication is optional. The server will request a client certificate,
      but it will proceed even if the client does not provide one.
    - `need`: Client authentication is mandatory. The server will terminate the connection if
      the client does not provide a valid certificate.
  * **Default**: `none` (if not specified, no client authentication is required).

### `forward-headers-strategy`  <Badge type="warning" text="Since 5.0" />
* **Description**: Configure Spring Boot to trust forwarded headers
* **Type**: String
  * **Allowed Values**: framework


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

* **Docker Compose Configuration**:
  * When SSL is enabled, the included `compose.yml` must be configured to mount the required
    certificate files into the container. Ensure that the SSL bundle files (certificates, private
    keys, and CA certificates) are properly mounted as volumes or bind mounts to make them
    accessible to the agent within the container.
  * Health checks in the compose configuration must be updated to use `https` instead of `http` when
    SSL is enabled. Using wget with `--no-check-certificate` or `--ca-certificate` might be
    necessary.
  * When client authentication is set to `need`, the health check must include a valid client
    certificate (via wget `--certificate` and `--private-key`) to successfully authenticate with the
    server. For `want` mode, client certificates are optional for health checks.

* **Reverse Proxy**:
  * If the agent runs behind a reverse proxy `forward-headers-strategy: framework` is necessary for 
    the correct construction of links. 


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
