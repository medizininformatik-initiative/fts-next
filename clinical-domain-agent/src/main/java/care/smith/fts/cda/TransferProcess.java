package care.smith.fts.cda;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.DataSelector;
import care.smith.fts.api.DeidentificationProvider;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class TransferProcess {
  private CohortSelector cohortSelector;

  private DataSelector dataSelector;

  private DeidentificationProvider deidentificationProvider;

  private BundleSender bundleSender;

  public TransferProcess(
      CohortSelector cohortSelector,
      DataSelector dataSelector,
      DeidentificationProvider deidentificationProvider,
      BundleSender bundleSender) {
    this.cohortSelector = cohortSelector;
    this.dataSelector = dataSelector;
    this.deidentificationProvider = deidentificationProvider;
    this.bundleSender = bundleSender;
  }
}
