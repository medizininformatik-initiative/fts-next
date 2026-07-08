# HTTP Codecs <Badge type="tip" text="All Agents" /> <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.7" />

FTSnext buffers non-streaming JSON payloads fully in memory before decoding them. This page
documents the cap on that buffer, which applies to every agent on both inbound (server) and
outbound (WebClient) decoding.

## Configuration Example

```yaml
spring.http.codecs:
  max-in-memory-size: 10MB
```

## Fields

### `spring.http.codecs.max-in-memory-size` <Badge type="warning" text="Since 5.7" />

* **Description**: Maximum number of bytes buffered in memory when decoding a non-streaming JSON
  body. Applies to inbound requests handled by the reactive server and to responses read by the
  agent's WebClient. A payload larger than this fails to decode with `DataBufferLimitException`.
* **Type**: `DataSize` (e.g. `256KB`, `10MB`)
* **Default**: `10MB` (Spring's own default is `256KB`)

## Notes

### Why the default is raised

The de-identification mappings exchanged between agents grow with the number of resources in a
patient's bundle:

* the **transport mapping** the Clinical Domain Agent sends to the Trust Center Agent, and
* the **secure mapping** the Trust Center Agent returns to the Research Domain Agent.

A patient with many resources produces mappings that exceed Spring's `256KB` default and abort the
transfer with `DataBufferLimitException`. FTSnext ships a `10MB` default so ordinary patients pass
without tuning.

### Overriding per deployment

The value can be overridden without editing the packaged configuration by setting the environment
variable `SPRING_HTTP_CODECS_MAX_IN_MEMORY_SIZE`, for example in a Docker Compose deployment:

```yaml
services:
  rd-agent:
    environment:
      SPRING_HTTP_CODECS_MAX_IN_MEMORY_SIZE: 32MB
```

Set the same value on every agent so the client and server ends of a transfer agree.

## References

* [Spring Boot `spring.http.codecs.max-in-memory-size` reference](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.web.spring.http.codecs.max-in-memory-size)
