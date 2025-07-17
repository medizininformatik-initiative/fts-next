package care.smith.fts.cda.impl;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.tca.TcaDomains;
import java.io.File;
import java.time.Duration;
import java.util.Optional;

public record DeidentifhirStepConfig(
    TCAConfig trustCenterAgent,
    Duration maxDateShift,
    File deidentifhirConfig,
    File scraperConfig,
    DateShiftPreserve dateShiftPreserve) {

  public DeidentifhirStepConfig(
      TCAConfig trustCenterAgent,
      Duration maxDateShift,
      File deidentifhirConfig,
      File scraperConfig,
      DateShiftPreserve dateShiftPreserve) {
    this.trustCenterAgent = trustCenterAgent;
    this.maxDateShift = maxDateShift;
    this.deidentifhirConfig = deidentifhirConfig;
    this.scraperConfig = scraperConfig;
    this.dateShiftPreserve = Optional.ofNullable(dateShiftPreserve).orElse(DateShiftPreserve.NONE);
  }

  public record TCAConfig(HttpClientConfig server, TcaDomains domains) {}
}
