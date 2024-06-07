package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.*;

public record TransferProcessConfig(
    @NotNull Map<String, ?> cohortSelector,
    @NotNull Map<String, ?> dataSelector,
    @NotNull Map<String, ?> deidentificationProvider,
    @NotNull Map<String, ?> bundleSender) {}
