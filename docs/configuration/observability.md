# Observability <Badge type="tip" text="All Agents" />

This document describes the configuration options available for exposing metrics and other status
endpoints.

## Configuration Structure

Below is the structure and explanation of the available options:

```yaml
management:
  endpoints:
    web:
      exposure:
        # Define which management endpoints are accessible via web
        include: [ "health", "info", "prometheus" ]
  metrics:
    distribution:
      slo:
        # Service Level Objectives (SLO) for server-side HTTP request response times (in milliseconds)
        http.server.requests: 25,100,250,500,1000,10000
        # Service Level Objectives (SLO) for client-side HTTP request response times (in milliseconds)
        http.client.requests: 25,100,250,500,1000,2000,3000,4000,5000,6000,7000,8000,9000,10000
        # Service Level Objectives (SLO) for deidentify operations (in milliseconds)
        deidentify: 25,100,250,500,1000,10000
```

## Fields

### `endpoints.web.exposure.include`

* **Description**: Specifies the management endpoints that should be accessible via the web.
* **Type**: List of Strings
* **Default**: Not specified
* **Example**:
  ```yaml
          include: [ "health", "info", "prometheus", "metrics" ]
  ```

#### Common Values:

* `"health"`: Provides health status information.
* `"info"`: Displays general application information.
* `"prometheus"`: Enables Prometheus metrics endpoint.

### `metrics.distribution.slo`

The `slo` field defines Service Level Objectives for various types of operations. Each key specifies
a type of operation, and its value is a comma-separated list of response time thresholds (in
milliseconds).

* **Type**: Map (Key: String, Value: List of Integers)
* **Examples**:
  ```yaml
          http.server.requests: 50,100,200,400,800
          http.client.requests: 100,200,300,500,1000,5000
          deidentify: 100,300,600,1200
  ```

#### Common Keys

* `http.server.requests` <Badge type="tip" text="All Agents" />
  * **Available in**: 
  * **Description**: SLOs for server-side HTTP request response times.
  * **Example Values**: `25,100,250,500,1000,10000`

* `http.client.requests` <Badge type="tip" text="All Agents" />
  * **Description**: SLOs for client-side HTTP request response times.
  * **Example Values**: `25,100,250,500,1000,2000,3000,4000,5000,10000`

* `deidentify` <Badge type="tip" text="Clinical Domain Agent" /><Badge type="tip" text="Research Domain Agent" />
  * **Description**: SLOs for deidentify operation times.
  * **Example Values**: `25,100,250,500,1000,10000`

* `fetchResearchMapping` <Badge type="tip" text="Research Domain Agent" />
  * **Description**: SLOs for fetching mappings from TCA.
  * **Example Values**: `5,10,25,100,250,500,1000,5000,10000`

* `sendBundleToHds` <Badge type="tip" text="Research Domain Agent" />
  * **Description**: SLOs for sending bundles to research domain HDS.
  * **Example Values**: `5,10,25,100,250,500,1000,5000,10000`

## Notes

* **Management Endpoints**: Ensure only necessary endpoints are exposed for security purposes.
* **SLO Thresholds**: Choose thresholds that reflect realistic performance expectations for each
  operation type.
* **Granularity**: Use detailed thresholds for precise performance tracking and monitoring.
