package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TransferProcessConfig(
    @NotNull Map<String, ?> cohortSelector,
    @NotNull Map<String, ?> dataSelector,
    @NotNull Map<String, ?> deidentificationProvider,
    @NotNull Map<String, ?> bundleSender) {

  @Override
  public String toString() {
    return "TransferProcessConfig{cohortSelector=%s, dataSelector=%s, deidentificationProvider=%s, bundleSender=%s}"
        .formatted(
            cohortSelector.keySet(),
            dataSelector.keySet(),
            deidentificationProvider.keySet(),
            bundleSender.keySet());
  }
}
