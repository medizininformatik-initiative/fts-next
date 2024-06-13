package care.smith.fts.cda;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.DataSelector;
import care.smith.fts.api.DeidentificationProvider;
import org.hl7.fhir.instance.model.api.IBaseBundle;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

public record TransferProcess<B extends IBaseBundle>(
    String project,
    CohortSelector cohortSelector,
    DataSelector<B> dataSelector,
    DeidentificationProvider<B> deidentificationProvider,
    BundleSender<B> bundleSender) {

  public TransferProcess {
    requireNonNull(emptyToNull(project));
    requireNonNull(cohortSelector);
    requireNonNull(dataSelector);
    requireNonNull(deidentificationProvider);
    requireNonNull(bundleSender);
  }
}
