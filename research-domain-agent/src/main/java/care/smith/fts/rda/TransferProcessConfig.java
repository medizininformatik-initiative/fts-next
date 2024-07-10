package care.smith.fts.rda;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TransferProcessConfig(
    @NotNull Map<String, ?> deidentificator, @NotNull Map<String, ?> bundleSender) {

  @Override
  public String toString() {
    return "TransferProcessConfig{deidentificator=%s, bundleSender=%s}"
        .formatted(deidentificator.keySet(), bundleSender.keySet());
  }
}
