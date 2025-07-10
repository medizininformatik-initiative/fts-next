# WebFlux <Badge type="tip" text="Trust Center Agent" /> <Badge type="warning" text="Since 5.0" />

## Configuration Example
If your agent is hosted at https://example.org/fts, set:
```
spring:
  # Prefix for all API routes (useful if the agent is served under a subpath)
  webflux.base-path: /fts
```
Without this, generated links (e.g., redirects, paging) may incorrectly point to `/` instead of 
`/fts.`

## Fields

### `spring.webflux.base-path` <Badge type="warning" text="Since 5.0" />
* **Description**: Specifies the base path of the URL. Necessary for link construction.
* **Type**: String
* **Default**: `/`

## Notes

* **Reverse Proxy**:
  * If the agent is accessed under a path, setting `spring.webflux.base-path` is necessary for the 
    correct construction of links.
  
## References

* **Spring Boot Documentation**:
  * [Spring Boot Base Path Reference](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.web.spring.webflux.base-path)
