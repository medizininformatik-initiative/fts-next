package care.smith.fts.rda.impl;

import static java.time.Duration.ofSeconds;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.rda.TransferProcessRunnerConfig;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.WebClientFactory;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import org.springframework.stereotype.Component;

@Component("fhirStoreBundleSender")
public class FhirStoreBundleSenderFactory
    implements BundleSender.Factory<FhirStoreBundleSenderConfig> {

  private final RetryStrategy retryStrategy;
  private final WebClientFactory clientFactory;
  private final BulkheadConfig bulkheadConfig;

  public FhirStoreBundleSenderFactory(
      WebClientFactory clientFactory,
      RetryStrategy retryStrategy,
      TransferProcessRunnerConfig runnerConfig) {
    this.clientFactory = clientFactory;
    this.retryStrategy = retryStrategy;
    this.bulkheadConfig =
        BulkheadConfig.custom()
            .maxConcurrentCalls(runnerConfig.maxConcurrentTransactions())
            .maxWaitDuration(ofSeconds(60))
            .build();
  }

  @Override
  public Class<FhirStoreBundleSenderConfig> getConfigType() {
    return FhirStoreBundleSenderConfig.class;
  }

  @Override
  public BundleSender create(
      BundleSender.Config commonConfig, FhirStoreBundleSenderConfig implConfig) {
    var client = clientFactory.create(implConfig.server());
    var bulkhead = Bulkhead.of("rda-store-%s".formatted(implConfig.project()), bulkheadConfig);
    return new FhirStoreBundleSender(client, retryStrategy, bulkhead);
  }
}
