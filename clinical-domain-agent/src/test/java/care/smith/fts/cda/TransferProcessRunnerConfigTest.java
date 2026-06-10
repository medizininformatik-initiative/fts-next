package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TransferProcessRunnerConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void bindsWhenSendConcurrencyWithinPrefetchWindow() {
    contextRunner
        .withPropertyValues("runner.maxConcurrentPatients=8", "runner.maxSendConcurrency=2")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var config = context.getBean(TransferProcessRunnerConfig.class);
              assertThat(config.maxConcurrentPatients).isEqualTo(8);
              assertThat(config.maxSendConcurrency).isEqualTo(2);
            });
  }

  @Test
  void failsWhenSendConcurrencyExceedsPrefetchWindow() {
    contextRunner
        .withPropertyValues("runner.maxConcurrentPatients=2", "runner.maxSendConcurrency=8")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .hasMessageContaining(
                      "maxSendConcurrency must not exceed runner.maxConcurrentPatients");
            });
  }

  @Test
  void failsWhenConcurrencyBelowMinimum() {
    contextRunner
        .withPropertyValues("runner.maxConcurrentPatients=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration
  @EnableConfigurationProperties(TransferProcessRunnerConfig.class)
  static class TestConfig {}
}
