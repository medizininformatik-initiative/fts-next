package care.smith.fts.test.connection_scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.*;
import org.reactivestreams.Publisher;
import reactor.test.StepVerifier.FirstStep;

public abstract class AbstractConnectionScenarioIT {

  protected interface TestStep<T> {
    MappingBuilder requestBuilder();

    Publisher<T> executeStep();

    default T returnValue() {
      return null;
    }

    default String acceptedContentType() {
      return null;
    }
  }

  protected abstract TestStep<?> createTestStep();

  @TestTemplate
  @ExtendWith(ConnectionScenariosExtension.class)
  void testConnectionScenarios(ConnectionScenario scenario) {
    scenario.execute(createTestStep());
  }

  static class ConnectionScenariosExtension implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
      return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
        ExtensionContext context) {
      return Stream.of(
          createContext("Connection Reset", this::executeConnectionReset),
          createContext("Connection Timeout", this::executeTimeout),
          createContext("First Request Fails", this::executeFirstRequestFails),
          createContext("First and Second Requests Fail", this::executeFirstAndSecondRequestsFail),
          createContext("All Requests Fail", this::executeAllRequestsFail),
          createContext("Response with wrong Content-Type", this::executeWrongContentType));
    }

    private TestTemplateInvocationContext createContext(
        String displayName, Consumer<TestStep<?>> executionStrategy) {
      return new TestTemplateInvocationContext() {
        @Override
        public String getDisplayName(int invocationIndex) {
          return displayName;
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
          return Collections.singletonList(
              new ParameterResolver() {
                @Override
                public boolean supportsParameter(
                    ParameterContext parameterContext, ExtensionContext extensionContext) {
                  return parameterContext.getParameter().getType() == ConnectionScenario.class;
                }

                @Override
                public Object resolveParameter(
                    ParameterContext parameterContext, ExtensionContext extensionContext) {
                  return new ConnectionScenario(executionStrategy);
                }
              });
        }
      };
    }

    private void executeTimeout(TestStep<?> step) {
      ScenarioMockUtil.testTimeout(step.requestBuilder(), step::executeStep);
    }

    private void executeConnectionReset(TestStep<?> step) {
      ScenarioMockUtil.testConnectionReset(step.requestBuilder(), step.executeStep());
    }

    private void executeFirstRequestFails(TestStep<?> step) {
      var firstStep = ScenarioMockUtil.firstRequestFails(step::requestBuilder, step.executeStep());
      verify(step, firstStep);
    }

    private static void verify(TestStep<?> step, FirstStep<?> firstStep) {
      if (step.returnValue() != null) {
        firstStep.assertNext(b -> assertThat(b).isEqualTo(step.returnValue())).verifyComplete();
      } else {
        firstStep.verifyComplete();
      }
    }

    private void executeFirstAndSecondRequestsFail(TestStep<?> step) {
      var firstStep =
          ScenarioMockUtil.firstAndSecondRequestsFail(step::requestBuilder, step.executeStep());
      verify(step, firstStep);
    }

    private void executeAllRequestsFail(TestStep<?> step) {
      ScenarioMockUtil.allRequestsFail(step.requestBuilder(), step.executeStep())
          .expectErrorSatisfies(ScenarioMockUtil::assertRetriesExhausted)
          .verify();
    }

    private void executeWrongContentType(TestStep<?> step) {
      if (step.acceptedContentType() != null) {
        ScenarioMockUtil.wrongContentType(step.requestBuilder(), step.executeStep())
            .expectError()
            .verify();
      }
    }
  }
}
