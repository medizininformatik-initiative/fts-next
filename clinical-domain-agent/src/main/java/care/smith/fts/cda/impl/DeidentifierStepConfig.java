package care.smith.fts.cda.impl;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.tca.TcaDomains;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.io.File;
import java.time.Duration;
import java.util.Optional;

public record DeidentifierStepConfig(
    TCAConfig trustCenterAgent,
    Duration maxDateShift,
    @JsonAlias("deidentifhirConfig") File deidentifierConfig,
    DateShiftPreserve dateShiftPreserve) {

  public DeidentifierStepConfig(
      TCAConfig trustCenterAgent,
      Duration maxDateShift,
      @JsonAlias("deidentifhirConfig") File deidentifierConfig,
      DateShiftPreserve dateShiftPreserve) {
    this.trustCenterAgent = trustCenterAgent;
    this.maxDateShift = maxDateShift;
    this.deidentifierConfig = deidentifierConfig;
    this.dateShiftPreserve = Optional.ofNullable(dateShiftPreserve).orElse(DateShiftPreserve.NONE);
  }

  public record TCAConfig(HttpClientConfig server, TcaDomains domains) {}
}
