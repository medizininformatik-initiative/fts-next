# Time Zone <Badge type="warning" text="Since 5.0" />

FTSnext serializes process timestamps (e.g. `createdAt`, `finishedAt`) as ISO-8601
strings with an explicit UTC offset. The offset is derived from the agent's
configured time zone.

## Setting the Time Zone

Set the `TZ` environment variable on the agent process. In a Docker Compose
deployment:

```yaml
services:
  cd-agent:
    environment:
      TZ: Europe/Berlin
```

If `TZ` is unset, `Europe/Berlin` is used.

## Notes

* The time zone only affects how timestamps are formatted in JSON responses.
  Comparisons and TTL cleanup (`runner.processTtl`) are unaffected.
* Valid values are IANA zone IDs (e.g. `Europe/Berlin`, `UTC`,
  `America/New_York`). See [the IANA tz database][iana].

[iana]: https://www.iana.org/time-zones
