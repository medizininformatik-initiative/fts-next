# Trustcenter Agent (TCA)

The TCA is responsible for cohort selection and de-identification.
In the current implementation the Greifswald
tools [gICS](https://www.ths-greifswald.de/forscher/gics/)
and [gPAS](https://www.ths-greifswald.de/forscher/gpas/)
are utilized for cohort selection and de-identification, respectively.

# Configuration

```yaml
consent:
  gics:
    fhir:
      baseUrl: http://gics:8080/ttp-fhir/fhir/gics
      defaultPageSize: 200
      auth:
        none: { }

security:
  endpoints:
    - path: /api/v2/cd/**
      role: cd
    - path: /api/v2/rd/**
      role: rd

deIdentification:
  keystoreUrl: redis://valkey:6379
  gpas:
    fhir:
      baseUrl: http://gpas:8080/ttp-fhir/fhir/gpas
      auth:
        none: { }
  transport:
    ttl: PT10M

spring:
  main:
    allow-bean-definition-overriding: true
  ssl:
    bundle:
      pem:
        server:
          keystore:
            certificate: target/test-classes/server.crt
            private-key: target/test-classes/server.key
          truststore:
            certificate: target/test-classes/ca.crt
        client:
          truststore:
            certificate: target/test-classes/ca.crt

server:
  ssl:
    bundle: server

test:
  webclient:
    cd-agent:
      ssl:
        bundle: client
    rd-agent:
      ssl:
        bundle: client
```
