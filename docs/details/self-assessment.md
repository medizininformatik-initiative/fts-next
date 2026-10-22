# Self-Assessment Endpoint

Each agent exposes `GET /api/v2/self-assessment` to help operators verify a deployment is
properly configured and that every external dependency is reachable. The endpoint is independent
from `/actuator/health` so that deep probing never affects liveness/readiness signals consumed by
Kubernetes.

## Response shape

The endpoint always returns HTTP 200 with the status inside the body:

```json
{
  "agent": "trust-center-agent",
  "overall": "DEGRADED",
  "components": [
    { "name": "gpas",     "kind": "fhir",   "url": "http://gpas:8080/...",   "status": "UP",      "latencyMs": 42 },
    { "name": "gics",     "kind": "fhir",   "url": "http://gics:8080/...",   "status": "DOWN",    "reason": "connection refused" },
    { "name": "redis",    "kind": "redis",  "url": "redis://keystore:6379",  "status": "UP",      "latencyMs": 3 },
    { "name": "keycloak", "kind": "oauth2", "status": "SKIPPED",             "reason": "not configured" }
  ],
  "projects": []
}
```

CDA and RDA additionally fill `projects[]`. Each entry lists every downstream URL discovered in
the project YAML and the result of a TCP/HTTP reachability probe against it:

```json
"projects": [
  {
    "name": "example",
    "valid": true,
    "status": "UP",
    "downstream": [
      { "name": "deidentificator.idMapper.trustCenterAgent.server", "kind": "http", "url": "http://tc-agent:8080", "status": "UP", "latencyMs": 17 },
      { "name": "bundleSender.fhirStore.server",                    "kind": "http", "url": "http://store:8080",    "status": "UP", "latencyMs": 21 }
    ]
  }
]
```

`overall` rolls up component and project status: `DOWN` > `DEGRADED` > `UP`. `SKIPPED` never
lowers the rollup.

## Configuration

| Property                    | Default | Effect                                 |
|-----------------------------|---------|----------------------------------------|
| `selfassessment.timeout`    | `PT3S`  | Per-probe timeout (ISO-8601 duration). |
| `selfassessment.concurrency`| `8`     | Parallel probes per assessment call.   |

## Probes

* **TCA** probes its fixed dependencies — gPAS (FHIR `/metadata` capability + required ops), gICS
  (capability + required ops, SKIPPED when not configured), Redis (Redisson bucket lookup), and
  Keycloak (`/.well-known/openid-configuration` on the configured `issuer-uri`, SKIPPED otherwise).
* **CDA / RDA** walk every loaded project's configuration, extract each `baseUrl`, and run a
  host-reachability probe. Any HTTP response (including 4xx from auth-protected endpoints) counts
  as reachable; only connect failures and timeouts produce `DOWN`.

## Authentication

The endpoint inherits the agent's global authentication configuration — the same gate that
already protects `/api/v2/projects`. With `auth=none` the endpoint is publicly readable, which
matches the exposure level of `/api/v2/projects`.

## Sample usage

```sh
curl -sS http://localhost:8080/api/v2/self-assessment | jq .
curl -sS -u user:pass http://localhost:8081/api/v2/self-assessment | jq .
```

Negative path:

```sh
docker stop gpas
curl -sS http://localhost:8080/api/v2/self-assessment | jq '.overall, .components[] | select(.name=="gpas")'
```
