package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class CohortSelectionSchedulerConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(CohortSelectionSchedulerConfig.class)
          .withBean(
              TransferProcessRunnerConfig.class,
              () -> new TransferProcessRunnerConfig(8, 2, 4, 4, Duration.ofDays(1)));

  @Test
  void providesDedicatedSchedulerDistinctFromGlobalParallel() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(Scheduler.class);
          assertThat(context.getBean("cohortSelectionScheduler", Scheduler.class))
              .isNotSameAs(Schedulers.parallel());
        });
  }

  @Test
  void disposesSchedulerWhenContextCloses() {
    var schedulerRef = new AtomicReference<Scheduler>();
    contextRunner.run(context -> schedulerRef.set(context.getBean(Scheduler.class)));
    assertThat(schedulerRef.get().isDisposed()).isTrue();
  }
}
