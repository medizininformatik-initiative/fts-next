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
    requireNonNull(emptyToNull(project));
    requireNonNull(rawConfig);
    requireNonNull(deidentificator);
    requireNonNull(bundleSender);
  }
}
