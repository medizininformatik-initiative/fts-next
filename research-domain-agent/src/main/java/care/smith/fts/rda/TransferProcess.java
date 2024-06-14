package care.smith.fts.rda;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.DeidentificationProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransferProcess(
    @NotBlank String project,
    @NotNull DeidentificationProvider deidentificationProvider,
    @NotNull BundleSender bundleSender) {}
