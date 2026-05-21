# HTTP Client <Badge type="tip" text="All Agents" /> <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.7" />

All FTSnext agents share a pooled outbound HTTP client. This page documents the connection
keep-alive timeout exposed for that pool.

## Configuration Example

```yaml
fts.http.client:
  keepalive-timeout: PT25S
```

## Fields

### `fts.http.client.keepalive-timeout` <Badge type="warning" text="Since 5.7" />

* **Description**: Maximum time an idle connection is kept in the pool before being closed.
  Applies to every outbound HTTP call an agent makes. Requires an agent restart to take effect.
* **Type**: ISO-8601 `Duration` (e.g. `PT25S`, `PT1M`)
* **Default**: `PT25S`

## Notes

### Pick a value below the lowest upstream idle timeout

If the keep-alive is **greater than or equal to** the idle timeout of any server the agent talks
to, that server will silently close pooled sockets first. The next request reuses a half-dead
socket, the write succeeds, the read returns EOF, and the call fails with:

```
WebClientRequestException: HTTP/1.1 header parser received no bytes
  caused by EOFException: EOF reached while reading
```

This typically manifests as a burst of failures on the *first* request to an upstream after an
idle gap, then stabilises once the pool is repopulated with fresh sockets.

### Per-agent upstreams

Each agent talks to a different set of servers; size the keep-alive against the tightest of
them.

| Agent | Outbound upstreams |
| --- | --- |
| cd-agent | cd-hds, tc-agent, rd-agent |
| rd-agent | rd-hds, tc-agent |
| tc-agent | gICS, gPAS |

Samply Blaze defaults to a 30s idle timeout. The `PT25S` default sits safely below it, the
tightest known upstream in the cluster. If you front your agents with infrastructure that closes
idle connections faster (e.g. a load balancer with a 15s idle), shorten this value accordingly.

### When to raise it

Raise the value if you control the upstreams and they keep idle connections longer than 25s. A
larger value reduces TCP/TLS handshake overhead for steady traffic. Always leave a margin (≈5s)
under the upstream idle.

### When the override takes effect

The value is applied at agent startup, before any request flows through the pool. Changing it
requires an agent restart.

## References

* [ISO-8601 `Duration` syntax](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence))
* [Samply Blaze configuration](https://github.com/samply/blaze/blob/main/docs/deployment/environment-variables.md)
