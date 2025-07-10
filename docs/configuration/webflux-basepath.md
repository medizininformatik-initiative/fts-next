# WebFlux Base Path <Badge type="tip" text="Trust Center Agent" /> <Badge type="warning" text="Since 5.0" />

## Configuration Example
```
 spring:
   # Set this if there is a path in the agent's URL
   webflux.base-path: /fts
```

## Fields

### `spring.webflux.base-path` <Badge type="warning" text="Since 5.0" />
* **Description**: Specifies the base path of the URL. Necessary for link construction.
* **Type**: String
* **Default**: `/`


* **Reverse Proxy**:
  * If the agent has a path in its URL setting `spring.webflux.base-path` is necessary for the 
    correct construction of links.
