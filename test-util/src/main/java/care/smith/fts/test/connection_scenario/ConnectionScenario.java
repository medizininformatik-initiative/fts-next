package care.smith.fts.test.connection_scenario;

import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT.TestStep;
import java.util.function.Consumer;

public class ConnectionScenario {
  private final Consumer<TestStep<?>> executionStrategy;

  public ConnectionScenario(Consumer<TestStep<?>> executionStrategy) {
    this.executionStrategy = executionStrategy;
  }

  public void execute(TestStep<?> step) {
    executionStrategy.accept(step);
  }
}
