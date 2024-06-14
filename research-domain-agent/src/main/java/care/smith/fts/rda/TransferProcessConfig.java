package care.smith.fts.rda;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TransferProcessConfig(
    @NotNull Map<String, ?> deidentificationProvider, @NotNull Map<String, ?> bundleSender) {}
