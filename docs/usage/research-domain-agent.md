# Research Domain Agent (RDA)

## Configuration


```yaml
projects:
  directory: "src/test/resources/projects"

spring:
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
    default:
      ssl:
        bundle: client

security:
  endpoints:
  - path: /api/v2/**
    role: cd-agent

management:
  endpoints:
    web:
      exposure:
        include: [ "health", "info", "prometheus" ]

  metrics:
    distribution.slo:
      http.server.requests: 25,100,250,500,1000,10000
      http.client.requests: 25,100,250,500,1000,10000
      fetchResearchMapping: 5,10,25,100,250,500,1000,5000,10000
      deidentify: 25,100,250,500,1000,10000
      sendBundleToHds: 25,50,100,250,500,1000,2000,5000,10000
```

### Project Configuration

This section describes the changes to the configuration settings for the medical data processing
system.

```yaml
deidentificator:
  deidentifhir:
    tca:
      server:
        baseUrl: http://tc-agent:8080
    deidentifhirConfig: /app/config/deidentifhir/TransportToRD.profile

bundleSender:
  fhirStore:
    server:
      baseUrl: http://rd-hds:8080/fhir
```

#### Deidentificator

The deidentificator component has been updated to use a different Deidentifhir configuration
profile.

```yaml
deidentificator:
  deidentifhir:
    tca:
      server:
        baseUrl: http://tc-agent:8080
    deidentifhirConfig: /app/config/deidentifhir/TransportToRD.profile
```

**Configuration Parameters:**

- `baseUrl`: The Trust Center Agent server endpoint
- `deidentifhirConfig`: Path to the updated DEIDENTIFHIR configuration profile,
  TransportToRD.profile

This new profile, TransportToRD.profile, is likely tailored for the specific requirements of
transmitting de-identified data to the Research Domain.

#### Bundle Sender

The bundle sender component has been updated to use a FHIR store instead of the Research Domain
Agent.

```yaml
bundleSender:
  fhirStore:
    server:
      baseUrl: http://rd-hds:8080/fhir
```

**Configuration Parameters:**

- `baseUrl`: The FHIR server endpoint for the Research Domain's FHIR store
