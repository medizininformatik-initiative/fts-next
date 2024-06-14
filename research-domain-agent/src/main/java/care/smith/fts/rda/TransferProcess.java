package care.smith.fts.rda;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.api.TransportBundle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.instance.model.api.IBaseBundle;

public record TransferProcess<B extends IBaseBundle>(
    @NotBlank String project,
    @NotNull DeidentificationProvider<TransportBundle<B>, B> deidentificationProvider,
    @NotNull BundleSender<B> bundleSender) {}
