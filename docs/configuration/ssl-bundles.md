# SSL <Badge type="tip" text="All Agents" />

This document provides an overview of the `spring.ssl.bundle` configuration options, which allow for
the setup of PEM-based SSL certificate and trust management for both server and client
communication.

## Configuration Structure

The `spring.ssl.bundle` configuration block is structured to define SSL certificates and private
keys for server and client keystores, as well as certificate authorities (CAs) for truststores.

```yaml
spring.ssl.bundle:
  pem:
    server:
      keystore:
        certificate: <path-to-server-certificate>
        private-key: <path-to-server-private-key>
      truststore:
        certificate: <path-to-ca-certificate>
    client:
      keystore:
        certificate: <path-to-client-certificate>
        private-key: <path-to-client-private-key>
      truststore:
        certificate: <path-to-ca-certificate>
```

## Fields

### `pem.server`

* #### `keystore`
  * `certificate`
    * **Description**: Path to the server's SSL certificate file.
    * **Example**: `target/test-classes/server.crt`
  * `private-key`
    * **Description**: Path to the private key corresponding to the server's SSL certificate.
    * **Example**: `target/test-classes/server.key`

* #### `truststore`
  * `certificate`
    * **Description**: Path to the certificate authority (CA) certificate used to validate
      incoming
      SSL connections on the server.
    * **Example**: `target/test-classes/ca.crt`

### `pem.client`

* #### `keystore`
  * `certificate`
    * **Description**: Path to the client's SSL certificate file.
    * **Example**: `target/test-classes/client-default.crt`
  * `private-key`
    * **Description**: Path to the private key corresponding to the client's SSL certificate.
    * **Example**: `target/test-classes/client-default.key`

* #### `truststore`
  * `certificate`
    * **Description**: Path to the certificate authority (CA) certificate used to validate
      server
      SSL connections from the client.
    * **Example**: `target/test-classes/ca.crt`

## Notes

* **File Paths**  
  The paths specified in the configuration should be accessible by the application at runtime.
  Relative paths like `target/test-classes/...` are typically used for development or testing. Use
  absolute paths in production for better reliability.

* **PEM Format**  
  Ensure that all the certificate and private key files are in PEM format. This format is widely
  supported and includes Base64 encoded data with `-----BEGIN` and `-----END` headers.

* **Compatibility**  
  This configuration is tailored for Spring Boot applications, particularly when fine-grained
  control over SSL certificate management is required for securing server and client communications.

## References

* [Spring Boot SSL Bundles](https://docs.spring.io/spring-boot/reference/features/ssl.html)