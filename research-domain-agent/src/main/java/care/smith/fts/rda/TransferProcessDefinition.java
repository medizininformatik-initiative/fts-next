package care.smith.fts.rda;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransferProcessDefinition(
    @NotBlank String project,
    @NotNull TransferProcessConfig rawConfig,
    @NotNull Deidentificator deidentificator,
    @NotNull BundleSender bundleSender) {

  public TransferProcessDefinition {
    requireNonNull(emptyToNull(project), "Project name must not be null or empty");
    requireNonNull(rawConfig, "Raw config must not be null");
    requireNonNull(deidentificator, "Deidentificator must not be null");
    requireNonNull(bundleSender, "Bundle sender must not be null");
  }
}
