package care.smith.fts.rda;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransferProcessDefinition(
    @NotBlank String project,
    @NotNull Deidentificator deidentificator,
    @NotNull BundleSender bundleSender) {}
