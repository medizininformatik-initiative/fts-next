package care.smith.fts.cda;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Provides a dedicated, Spring-managed Reactor {@link Scheduler} for the CPU-bound grouping of
 * patients with their consents during cohort selection. Sizing it to {@code
 * cohortSelectionConcurrency} caps the total threads this work may use and isolates it from the
 * JVM-wide {@link Schedulers#parallel()} pool that is shared with all other Reactor work. The bean
 * is a singleton, so concurrent transfer processes share this one bounded pool.
 */
@Configuration
class CohortSelectionSchedulerConfig {

  @Bean(destroyMethod = "dispose")
  Scheduler cohortSelectionScheduler(TransferProcessRunnerConfig config) {
    return Schedulers.newParallel("cohort-selection", config.cohortSelectionConcurrency(), true);
  }
}
