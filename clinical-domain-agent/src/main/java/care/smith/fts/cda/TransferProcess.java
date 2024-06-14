package care.smith.fts.cda;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.*;
import care.smith.fts.cda.services.deidentifhir.ConsentedPatientBundle;
import org.hl7.fhir.instance.model.api.IBaseBundle;

public record TransferProcess<B extends IBaseBundle>(
    String project,
    CohortSelector cohortSelector,
    DataSelector<B> dataSelector,
    DeidentificationProvider<ConsentedPatientBundle<B>, TransportBundle<B>>
        deidentificationProvider,
    BundleSender<B> bundleSender) {

  public TransferProcess {
    requireNonNull(emptyToNull(project));
    requireNonNull(cohortSelector);
    requireNonNull(dataSelector);
    requireNonNull(deidentificationProvider);
    requireNonNull(bundleSender);
  }
}
