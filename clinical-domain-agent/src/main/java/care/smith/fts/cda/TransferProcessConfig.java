package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TransferProcessConfig(
    @NotNull Map<String, ?> cohortSelector,
    @NotNull Map<String, ?> dataSelector,
    @NotNull Map<String, ?> deidentificator,
    @NotNull Map<String, ?> bundleSender) {

  @Override
  public String toString() {
    return "TransferProcessConfig{cohortSelector=%s, dataSelector=%s, deidentificator=%s, bundleSender=%s}"
        .formatted(
            printEntry(cohortSelector),
            printEntry(dataSelector),
            printEntry(deidentificator),
            printEntry(bundleSender));
  }

  private Object printEntry(Map<String, ?> v) {
    return v != null ? v.keySet() : "null";
  }
}
