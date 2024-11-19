# Server <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" /><Badge type="tip" text="Trust Center Agent" />

This documentation outlines the configuration options available for the server section of your YAML configuration. Below is a structured explanation of the fields, their meanings, and additional references.

## **Configuration Structure**

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

### **server**
The `server` block contains settings related to the server's SSL (Secure Sockets Layer) configuration.

#### **ssl**
This section manages the SSL configuration of the server. SSL ensures secure communication between the client and the server by encrypting data.

##### **bundle**
- **Description**: Specifies the SSL bundle to use.
- **Type**: String
- **Default**: `server`
- **Usage**: The name of the SSL bundle, which contains the SSL certificates and key configurations.

##### **client-auth**
- **Description**: Defines the client authentication requirement for SSL communication.
- **Type**: String
- **Allowed Values**:
    - `want`: Client authentication is optional. The server will request a client certificate, but it will proceed even if the client does not provide one.
    - `need`: Client authentication is mandatory. The server will terminate the connection if the client does not provide a valid certificate.
- **Default**: `none` (if not specified, no client authentication is required).
- **Notes**:
    - Use `want` if you want to allow both authenticated and unauthenticated clients.
    - Use `need` to ensure secure connections where only authenticated clients can access the server.

## **Notes**

* **SSL Configuration**:
    - Ensure that the SSL bundle contains all necessary files, such as the server certificate, private key, and CA certificate.
    - Misconfiguration may lead to connection failures or weakened security.

* **Client Authentication**:
    - `want` is suitable for environments where backward compatibility with unauthenticated clients is needed.
    - `need` is recommended for high-security environments to ensure only trusted clients can establish a connection.

* **Default Behavior**:
    - If the `ssl` block is commented out or omitted, SSL is not enabled, and communication occurs over plain text.

## **References**

1. **Spring Boot Documentation**:
    - [Spring Boot SSL Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#application-properties.server.ssl)
    - [Spring Boot Security Reference](https://docs.spring.io/spring-security/site/docs/current/reference/html5/)

2. **SSL and TLS Concepts**:
    - [What is SSL?](https://www.ssl.com/faqs/what-is-ssl/)
    - [TLS vs. SSL](https://www.cloudflare.com/learning/ssl/what-is-ssl/)

3. **Best Practices**:
    - [OWASP: Transport Layer Protection Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Protection_Cheat_Sheet.html)

---

For further details on configuring `server.ssl` or troubleshooting SSL issues, refer to the provided links or consult your system administrator.