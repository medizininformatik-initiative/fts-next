package care.smith.fts.cda;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.DataSelector;
import care.smith.fts.api.DeidentificationProvider;
import lombok.Getter;
import lombok.ToString;
import org.hl7.fhir.instance.model.api.IBaseBundle;

@Getter
@ToString
public class TransferProcess<B extends IBaseBundle> {
  private CohortSelector cohortSelector;

  private DataSelector<B> dataSelector;

  private DeidentificationProvider<B> deidentificationProvider;

  private BundleSender<B> bundleSender;

  public TransferProcess(
      CohortSelector cohortSelector,
      DataSelector<B> dataSelector,
      DeidentificationProvider<B> deidentificationProvider,
      BundleSender<B> bundleSender) {
    this.cohortSelector = cohortSelector;
    this.dataSelector = dataSelector;
    this.deidentificationProvider = deidentificationProvider;
    this.bundleSender = bundleSender;
  }
}
