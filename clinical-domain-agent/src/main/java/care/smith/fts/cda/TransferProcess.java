package care.smith.fts.cda;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.DeidentificationProvider;

public record TransferProcess(
    String project,
    CohortSelector cohortSelector,
    DataSelector dataSelector,
    DeidentificationProvider deidentificationProvider,
    BundleSender bundleSender) {

  public TransferProcess {
    requireNonNull(emptyToNull(project));
    requireNonNull(cohortSelector);
    requireNonNull(dataSelector);
    requireNonNull(deidentificationProvider);
    requireNonNull(bundleSender);
  }
}
