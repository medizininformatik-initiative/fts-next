# HTTP Client <Badge type="tip" text="All Agents" /> <Badge type="tip" text="Optional" /> <Badge type="warning" text="Since 5.7" />

All FTSnext agents share a pooled outbound HTTP client backed by Reactor Netty. This page documents
the connection-pool idle timeout exposed for that pool.

## Configuration Example

```yaml
fts.http.client:
  max-idle-time: PT25S
```

## Fields

### `fts.http.client.max-idle-time` <Badge type="warning" text="Since 5.7" />

* **Description**: Maximum time an idle connection is kept in the pool before it is evicted.
  Applies to every outbound HTTP call an agent makes, on both plain and SSL/mTLS connections.
  Requires an agent restart to take effect.
* **Type**: ISO-8601 `Duration` (e.g. `PT25S`, `PT1M`)
* **Default**: `PT25S`

## Notes

### How idle connections are handled

The Reactor Netty connection pool is configured programmatically per agent (no JVM-global system
property). Two mechanisms keep pooled sockets healthy:

* **Event-driven eviction**: a pooled connection sits on a Netty event loop, so when an upstream
  closes an idle socket the resulting FIN triggers `channelInactive` and the connection is evicted
  from the pool *before* it can be reused.
* **`max-idle-time`**: connections idle longer than this are evicted on acquisition, and a
  background sweep removes them even without traffic.

Together these largely remove the "reuse a half-dead socket" failure that the JDK client was prone
to, where the next request after an idle gap would write successfully but read EOF and fail with:

```
WebClientRequestException: HTTP/1.1 header parser received no bytes
  caused by EOFException: EOF reached while reading
```

The connection's total lifetime is additionally capped (max life time) so long-lived pools pick up
upstream changes (e.g. DNS or load-balancer rotation).

### Pick a value below the lowest upstream idle timeout

Eviction is event-driven, but the time-to-first-event window is not zero. Keeping `max-idle-time`
below the idle timeout of every server the agent talks to ensures the pool drops a connection on
its own schedule rather than racing the upstream's close.

### Per-agent upstreams

Each agent talks to a different set of servers; size `max-idle-time` against the tightest of them.

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
