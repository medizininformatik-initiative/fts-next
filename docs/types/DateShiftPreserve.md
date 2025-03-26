# DateShiftPreserve <Badge type="warning" text="Since 5.2" />

An enumeration that specifies how date shifting should be preserved when transforming dates.

## Values

| Enum Value | Description                                                      |
|------------|------------------------------------------------------------------|
| `NONE`     | No special preservation of date characteristics during shifting. |
| `WEEKDAY`  | Shift dates by multiples of 7 days.                              |
| `DAYTIME`  | Shift dates by multiples of 24 hours.                            |
