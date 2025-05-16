package care.smith.fts.tca.consent;

import static org.junit.jupiter.api.Assertions.*;

import care.smith.fts.tca.consent.configuration.GicsConfiguration;
import care.smith.fts.tca.rest.ConsentController;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import care.smith.fts.util.tca.ConsentFetchRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location=classpath:application-no-gics.yaml"})
@Import(TestWebClientFactory.class)
public class NoGicsIT {
  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  @Autowired private ApplicationContext context;

  private WebTestClient testClient;

  @BeforeEach
  public void init(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    var client = factory.webClient("https://localhost:" + port, "cd-agent");
    testClient = WebTestClient.bindToController(client).build();
  }

  @Test
  void noGicsBeansArePresent() {
    assertThrows(
        NoSuchBeanDefinitionException.class, () -> context.getBean(GicsConfiguration.class));
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> context.getBean(GicsFhirConsentedPatientsProvider.class));
    assertThrows(
        NoSuchBeanDefinitionException.class, () -> context.getBean(ConsentController.class));
  }

  @Test
  void noFetchApiEndpointAvailable() {
    ConsentFetchRequest fetchRequest =
        new ConsentFetchRequest(
            "testDomain",
            Set.of("policy1", "policy2"),
            "http://example.org/policy",
            "http://example.org/patient",
            List.of("patient1", "patient2"));

    testClient
        .post()
        .uri("/api/v2/cd/consented-patients/fetch")
        .contentType(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue(fetchRequest))
        .exchange()
        .expectStatus()
        .isNotFound(); // Expect 404 since the endpoint should not exist
  }

  @Test
  void noFetchAllApiEndpointAvailable() {
    ConsentFetchAllRequest fetchAllRequest =
        new ConsentFetchAllRequest(
            "testDomain", Set.of("policy1", "policy2"), "http://example.org/policy");

    testClient
        .post()
        .uri("/api/v2/cd/consented-patients/fetch-all")
        .contentType(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue(fetchAllRequest))
        .exchange()
        .expectStatus()
        .isNotFound(); // Expect 404 since the endpoint should not exist
  }
}
