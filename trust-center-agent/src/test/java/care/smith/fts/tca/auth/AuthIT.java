package care.smith.fts.tca.auth;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.tca.deidentification.MappingProvider;
import care.smith.fts.test.AbstractAuthIT;
import care.smith.fts.util.tca.SecureMappingResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class AuthIT {

  @Nested
  @ActiveProfiles("auth_basic")
  class BasicAuthIT extends AbstractAuthIT {
    @MockitoBean RedissonClient redisClient;
    @MockitoBean private MappingProvider mappingProvider;

    public BasicAuthIT() {
      super("rd-agent");
    }

    @BeforeEach
    void setup() {
      String transferId = "any";
      var mockResponse =
          Mono.just(
              new SecureMappingResponse(
                  Map.of("tid1", "pid1", "tid2", "pid2"), Duration.ofHours(2)));
      when(mappingProvider.fetchSecureMapping(transferId)).thenReturn(mockResponse);
    }

    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client
          .post()
          .uri("/api/v2//rd/secure-mapping")
          .contentType(APPLICATION_JSON)
          .bodyValue("any");
    }
  }

  @Nested
  @ActiveProfiles("auth_oauth2")
  class OAuth2AuthIT extends AbstractAuthIT {
    @MockitoBean RedissonClient redisClient;
    @MockitoBean private MappingProvider mappingProvider;

    public OAuth2AuthIT() {
      super("rd-agent");
    }

    @BeforeEach
    void setup() {
      String transferId = "any";
      var mockResponse =
          Mono.just(
              new SecureMappingResponse(
                  Map.of("tid1", "pid1", "tid2", "pid2"), Duration.ofHours(2)));
      when(mappingProvider.fetchSecureMapping(transferId)).thenReturn(mockResponse);
    }

    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client
          .post()
          .uri("/api/v2//rd/secure-mapping")
          .contentType(APPLICATION_JSON)
          .bodyValue("any");
    }
  }

  @Nested
  @ActiveProfiles("auth_cert")
  class CertAuthIT extends AbstractAuthIT {
    @MockitoBean RedissonClient redisClient;
    @MockitoBean private MappingProvider mappingProvider;

    public CertAuthIT() {
      super("rd-agent");
    }

    @BeforeEach
    void setup() {
      String transferId = "any";
      var mockResponse =
          Mono.just(
              new SecureMappingResponse(
                  Map.of("tid1", "pid1", "tid2", "pid2"), Duration.ofHours(2)));
      when(mappingProvider.fetchSecureMapping(transferId)).thenReturn(mockResponse);
    }

    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client
          .post()
          .uri("/api/v2//rd/secure-mapping")
          .contentType(APPLICATION_JSON)
          .bodyValue("any");
    }
  }
}
