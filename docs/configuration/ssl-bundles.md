# SSL Bundles <Badge type="tip" text="All Agents" /> <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.0" />

This page documents the `spring.ssl.bundle` section of the FTSnext agent configuration file
(`application.yaml`), which allows for the setup of PEM-based SSL certificate and trust management
for both server and client communication. It is structured to define SSL certificates and private
keys for server and client keystores, as well as certificate authorities (CAs) for truststores.

## Security Best Practices

In line with OSSF Best Practices [crypto_pfs][crypto_pfs] and [crypto_keylength][crypto_keylength],
the **FTSnext agent provides full configurability** to enable the use of
strong cryptographic algorithms, key lengths, and ciphers via [Spring Boot SSL Bundles][spring-ssl]
configuration. It is the responsibility of the deployer to configure these settings appropriately.

To enforce strong cryptography, configure:

* `enabled-protocols` to `TLSv1.2` or [`TLSv1.3`][tls13]
* `ciphers` to only include ECDHE-based cipher suites
* Certificates with RSA 2048+ or ECDSA (P-256 or better)

For general TLS security recommendations, see:

* [OWASP Transport Layer Protection Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Protection_Cheat_Sheet.html)
* [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/)

## Configuration Example

```yaml
spring.ssl.bundle:
  pem:
    server:
      keystore:
        certificate: file:<path-to-server-certificate>
        private-key: file:<path-to-server-private-key>
        private-key-password: <password>
      truststore:
        certificate: file:<path-to-ca-certificate>

      # Recommended for enforcing strong cryptography
      enabled-protocols: TLSv1.2,TLSv1.3
      ciphers:
      - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256

    client: # Name is freely assignable, can be then be used in project http client configurations
      keystore:
        certificate: file:<path-to-client-certificate>
        private-key: file:<path-to-client-private-key>
        private-key-password: <password>
      truststore:
        certificate: file:<path-to-ca-certificate>
      [ ... ]
```

## Fields

### `pem.server` <Badge type="warning" text="Since 5.0" />

* #### `keystore` <Badge type="warning" text="Since 5.0" />
  * `certificate`
    * **Description**: Path to the server's SSL certificate file.
    * **Example**: `file:/path/to/server.crt`
  * `private-key`
    * **Description**: Path to the private key corresponding to the server's SSL certificate.
    * **Example**: `file:/path/to/server.key`
  * `private-key-password`
    * **Description**: Password used to protect the server's private key file, if encrypted.
    * **Example**: `secret`

* #### `truststore` <Badge type="warning" text="Since 5.0" />
  * `certificate`
    * **Description**: Path to the certificate authority (CA) certificate used to validate
      incoming SSL connections on the server.
    * **Example**: `file:/path/to/ca.crt`

* #### `enabled-protocols` <Badge type="warning" text="Since 5.0" />
  * **Description**: Comma-separated list of supported TLS protocol versions for server connections.
  * **Example**: `TLSv1.2,TLSv1.3` to ensure strong cryptography and forward secrecy.

* #### `ciphers` <Badge type="warning" text="Since 5.0" />
  * **Description**: List of supported cipher suites for server connections, ordered by preference.
  * **Example**: Limit to ECDHE-based suites for Perfect Forward Secrecy
    ```yaml
    ciphers:
      - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
    ```

### `pem.client` <Badge type="warning" text="Since 5.0" />

* #### `keystore` <Badge type="warning" text="Since 5.0" />
  * `certificate`
    * **Description**: Path to the client's SSL certificate file.
    * **Example**: `file:/path/to/client-default.crt`
  * `private-key`
    * **Description**: Path to the private key corresponding to the client's SSL certificate.
    * **Example**: `file:/path/to/client-default.key`
  * `private-key-password`
    * **Description**: Password used to protect the clients's private key file, if encrypted.
    * **Example**: `secret`

* #### `truststore` <Badge type="warning" text="Since 5.0" />
  * `certificate`
    * **Description**: Path to the certificate authority (CA) certificate used to validate server
      SSL connections from the client.
    * **Example**: `file:/path/to/ca.crt`

* #### `enabled-protocols` <Badge type="warning" text="Since 5.0" />
  * **Description**: Comma-separated list of supported TLS protocol versions for client connections.
  * **Example**: `TLSv1.2,TLSv1.3` to ensure strong cryptography and forward secrecy.

* #### `ciphers` <Badge type="warning" text="Since 5.0" />
  * **Description**: List of supported cipher suites for client connections, ordered by preference.
  * **Example**: Limit to ECDHE-based suites for Perfect Forward Secrecy
    ```yaml
    ciphers:
      - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
    ```

## Notes

* **File Paths**  
  The file paths must begin with either `file:` or `classpath:`.

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

* [Spring Boot SSL Bundles][spring-ssl]
* [FLOSS Best Practices Criteria (Passing Badge)](https://www.bestpractices.dev/en/criteria/0.0)
* [The Transport Layer Security (TLS) Protocol Version 1.3][tls13]

[spring-ssl]: https://docs.spring.io/spring-boot/reference/features/ssl.html

[crypto_pfs]: https://www.bestpractices.dev/en/criteria/0#0.crypto_pfs

[crypto_keylength]: https://www.bestpractices.dev/en/criteria/0#0.crypto_keylength

[tls13]: https://datatracker.ietf.org/doc/html/rfc8446