package care.smith.fts.rda;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.DataSelector;
import care.smith.fts.api.DeidentificationProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.instance.model.api.IBaseBundle;

public record TransferProcess<B extends IBaseBundle>(
        @NotBlank String project,
        @NotNull DeidentificationProvider<B> deidentificationProvider,
        @NotNull BundleSender<B> bundleSender) {
}
