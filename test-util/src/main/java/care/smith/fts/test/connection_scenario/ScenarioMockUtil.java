package care.smith.fts.test.connection_scenario;

import static care.smith.fts.test.MockServerUtil.connectionReset;
import static care.smith.fts.test.MockServerUtil.delayedResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static reactor.test.StepVerifier.create;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import reactor.core.Exceptions;
import reactor.test.StepVerifier;
import reactor.test.StepVerifier.FirstStep;

public interface ScenarioMockUtil {

  static void testTimeout(MappingBuilder builder, Supplier<? extends Publisher<?>> fn) {
    stubFor(builder.willReturn(delayedResponse()));
    StepVerifier.withVirtualTime(fn)
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(12))
        .expectError(TimeoutException.class)
        .verify();
  }

  static void testConnectionReset(MappingBuilder builder, Publisher<?> fn) {
    stubFor(builder.willReturn(connectionReset()));
    create(fn).expectError(Exception.class).verify();
  }

  static FirstStep<?> firstRequestFails(Supplier<MappingBuilder> builder, Publisher<?> fn) {
    stubFor(
        builder
            .get()
            .inScenario("retry-scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("second-attempt"));

    stubFor(
        builder
            .get()
            .inScenario("retry-scenario")
            .whenScenarioStateIs("second-attempt")
            .willReturn(aResponse().withStatus(200)));

    return create(fn);
  }

  static FirstStep<?> firstAndSecondRequestsFail(
      Supplier<MappingBuilder> builder, Publisher<?> fn) {

    stubFor(
        builder
            .get()
            .inScenario("retry-scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("second-attempt"));

    stubFor(
        builder
            .get()
            .inScenario("retry-scenario")
            .whenScenarioStateIs("second-attempt")
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("third-attempt"));

    stubFor(
        builder
            .get()
            .inScenario("retry-scenario")
            .whenScenarioStateIs("third-attempt")
            .willReturn(aResponse().withStatus(200)));

    return create(fn);
  }

  static FirstStep<?> allRequestsFail(MappingBuilder builder, Publisher<?> fn) {
    stubFor(builder.willReturn(aResponse().withStatus(500)));
    return create(fn);
  }

  static void assertRetriesExhausted(Throwable throwable) {
    assertThat(throwable)
        .matches(Exceptions::isRetryExhausted)
        .hasMessageContaining("Retries exhausted: 3/3");
  }

  static FirstStep<?> wrongContentType(MappingBuilder builder, Publisher<?> fn) {
    stubFor(builder.willReturn(ok().withHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)));
    return create(fn);
  }
}
