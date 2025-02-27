package care.smith.fts.tca.deidentification;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static org.springframework.http.HttpStatus.I_AM_A_TEAPOT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.util.error.fhir.NoFhirServerException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@SpringBootTest
@WireMockTest
public class GpasClientIT {
  @Autowired WebClient.Builder httpClientBuilder;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  private static WireMock wireMock;

  GpasClient gpasClient;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired MeterRegistry meterRegistry) {
    var address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();
    gpasClient = new GpasClient(httpClientBuilder.baseUrl(address).build(), meterRegistry);
  }

  @Test
  void responseIsNotFHIR() {
    wireMock.register(
        post("/$pseudonymizeAllowCreate")
            .willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    wireMock.register(
        get("/metadata")
            .willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    create(gpasClient.fetchOrCreatePseudonyms("domain", "id"))
        .expectError(NoFhirServerException.class)
        .verify();
  }
}
