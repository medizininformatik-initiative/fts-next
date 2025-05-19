# External Cohort Selector <Badge type="tip" text="Clinical Domain Agent" /> <Badge type="warning" text="Since 5.3" />

The External Cohort Selector uses all provided PIDs without explicit consent checks.
The process must be started with a manually provided cohort, see [execution](../../usage/execution#manual-cohort) for details.

The External Cohort Selector only works, if `ignoreConsent=true` in [dataSelector](../data-selector) is set, as no consented period is set.  

## Example Configuration

```yaml
external:
```
