package care.smith.fts.test;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Slf4j
@TestConfiguration
@ConfigurationProperties(prefix = "test.webclient.auth")
@Setter
public class TestWebClientAuth {

  WebClientTestBasicAuth basic = null;

  interface WebClientTestAuthProvider {
    Mono<ClientResponse> filter(ClientRequest req, ExchangeFunction next);
  }

  @Bean
  WebTestClientBuilderCustomizer webTestClientAuthCustomizer() {
    return builder -> builder.filter(authProvider()::filter);
  }

  @Bean
  WebClientCustomizer testWebClientAuthCustomizer() {
    return builder -> builder.filter(authProvider()::filter);
  }

  private WebClientTestAuthProvider authProvider() {
    return Stream.<WebClientTestAuthProvider>of(basic)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(new WebClientTestNoneAuth());
  }

  record WebClientTestBasicAuth(List<AuthSpec> endpoints)
      implements WebClientTestAuthProvider {
    private static final PathMatcher MATCHER = new AntPathMatcher();

    WebClientTestBasicAuth {
      log.debug("Configuring 'basic auth' for test clients: {}", endpoints);
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest req, ExchangeFunction next) {
      return next.exchange(
          endpoints.stream()
              .filter(spec -> MATCHER.match(spec.path(), req.url().getPath()))
              .findFirst()
              .map(spec -> spec.setHeader(req))
              .orElse(req));
    }

    record AuthSpec(String username, String password, String path) {
      private ClientRequest setHeader(ClientRequest req) {
        log.debug("Add basic auth for path '{}'", this.path());
        return ClientRequest.from(req)
            .headers(headers -> headers.setBasicAuth(this.username(), this.password()))
            .build();
      }
    }
  }

  record WebClientTestNoneAuth() implements WebClientTestAuthProvider {
    WebClientTestNoneAuth {
      log.debug("Configuring 'none auth' for test clients");
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest req, ExchangeFunction next) {
      return next.exchange(req);
    }
  }
}
