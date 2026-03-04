package care.smith.fts.cda.impl;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.tca.TcaDomains;
import java.io.File;
import java.time.Duration;
import java.util.Optional;

public record FhirPseudonymizerConfig(
    HttpClientConfig serviceUrl,
    File anonymizationConfig,
    TCAConfig trustCenterAgent,
    Duration maxDateShift,
    DateShiftPreserve dateShiftPreserve) {

  public FhirPseudonymizerConfig(
      HttpClientConfig serviceUrl,
      File anonymizationConfig,
      TCAConfig trustCenterAgent,
      Duration maxDateShift,
      DateShiftPreserve dateShiftPreserve) {
    this.serviceUrl = serviceUrl;
    this.anonymizationConfig = anonymizationConfig;
    this.trustCenterAgent = trustCenterAgent;
    this.maxDateShift = maxDateShift;
    this.dateShiftPreserve = Optional.ofNullable(dateShiftPreserve).orElse(DateShiftPreserve.NONE);
  }

  public record TCAConfig(HttpClientConfig server, TcaDomains domains) {}
}
