# SSL Bundles <Badge type="tip" text="All Agents" /> <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.0" />

This page documents the `spring.ssl.bundle` section of the FTSnext agent configuration file
(`application.yaml`), which allows for the setup of PEM-based SSL certificate and trust management
for both server and client communication. It is structured to define SSL certificates and private
keys for server and client keystores, as well as certificate authorities (CAs) for truststores.

## Configuration Example

```yaml
spring.ssl.bundle:
  pem:
    server:
      keystore:
        certificate: <path-to-server-certificate>
        private-key: <path-to-server-private-key>
        private-key-password: <password>
      truststore:
        certificate: <path-to-ca-certificate>
    client:
      keystore:
        certificate: <path-to-client-certificate>
        private-key: <path-to-client-private-key>K
        private-key-password: <password>
      truststore:
        certificate: <path-to-ca-certificate>
```

## Fields

### `pem.server` <Badge type="warning" text="Since 5.0" />

* #### `keystore` <Badge type="warning" text="Since 5.0" />
  * `certificate`
    * **Description**: Path to the server's SSL certificate file.
    * **Example**: `target/test-classes/server.crt`
  * `private-key`
    * **Description**: Path to the private key corresponding to the server's SSL certificate.
    * **Example**: `target/test-classes/server.key`
* `private-key-password`
  * **Description**: Password used to protect the server's private key file. This is required if the
    private key is encrypted.
  * **Example**: `secret`

* #### `truststore` <Badge type="warning" text="Since 5.0" />
  * `certificate`
    * **Description**: Path to the certificate authority (CA) certificate used to validate
      incoming
      SSL connections on the server.
    * **Example**: `target/test-classes/ca.crt`

### `pem.client` <Badge type="warning" text="Since 5.0" />

* #### `keystore` <Badge type="warning" text="Since 5.0" />
  * `certificate`
    * **Description**: Path to the client's SSL certificate file.
    * **Example**: `target/test-classes/client-default.crt`
  * `private-key`
    * **Description**: Path to the private key corresponding to the client's SSL certificate.
    * **Example**: `target/test-classes/client-default.key`
  * `private-key-password`
    * **Description**: Password used to protect the server's private key file. This is required if
      the private key is encrypted.
    * **Example**: `secret`

* #### `truststore` <Badge type="warning" text="Since 5.0" />
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
