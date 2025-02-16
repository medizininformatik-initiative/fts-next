package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.auth.HttpServerAuthConfig.Endpoint;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authorization.AuthorizationWebFilter;
import reactor.core.publisher.Mono;

class HttpServerOAuth2AuthTest {
  static class TestHttpServerOAuth2Auth extends HttpServerOAuth2Auth {
    TestHttpServerOAuth2Auth() {
      super("https://test-issuer");
    }

    @Override
    protected ReactiveJwtDecoder createJwtDecoder() {
      return token -> {
        Map<String, Object> claims = new HashMap<>();
        return Mono.just(
            new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                claims));
      };
    }
  }

  TestHttpServerOAuth2Auth auth = new TestHttpServerOAuth2Auth();

  @Test
  void configure() {
    var security = ServerHttpSecurity.http();
    var chain = auth.configure(security).build();
    var filters = chain.getWebFilters().collectList().block();

    assertThat(filters)
        .filteredOn(filter -> filter instanceof AuthenticationWebFilter)
        .isNotEmpty();
  }

  @Test
  void filterShouldRequireAuthenticationForEndpointPath() {
    var security = ServerHttpSecurity.http();
    var endpoint = new Endpoint("https://test-issuer", "");
    var chain = auth.filter(endpoint, security).build();
    var filters = chain.getWebFilters().collectList().block();

    assertThat(filters).filteredOn(filter -> filter instanceof AuthorizationWebFilter).hasSize(1);

    // Verify the security rules by attempting access
    var exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("https://test-issuer").build());

    var authenticationFilter =
        filters.stream()
            .filter(filter -> filter instanceof AuthorizationWebFilter)
            .map(filter -> (AuthorizationWebFilter) filter)
            .findFirst()
            .orElseThrow();

    // Test unauthorized access
    var result =
        authenticationFilter
            .filter(exchange, request -> Mono.error(new RuntimeException("Should not reach here")))
            .onErrorResume(AccessDeniedException.class, e -> Mono.empty())
            .block();

    assertThat(result).isNull(); // Access should be denied
  }

  @Test
  void configureUsers() {
    assertThat(auth.configureUsers()).isNull();
  }

  @Test
  void testToString() {
    assertThat(auth.toString()).isEqualTo("OAuth2");
  }
}
