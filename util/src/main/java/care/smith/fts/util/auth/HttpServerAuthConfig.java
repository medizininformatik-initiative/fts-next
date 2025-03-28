package care.smith.fts.util.auth;

import care.smith.fts.util.auth.HttpServerAuthMethod.AuthMethod;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Slf4j
@EnableWebFluxSecurity
@ConfigurationProperties(prefix = "security")
@Setter
public class HttpServerAuthConfig {

  private AuthMethod auth = HttpServerAuthMethod.NONE;
  private List<Endpoint> endpoints = List.of();

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    var method = authMethod(auth);
    http.csrf(CsrfSpec::disable);

    log.debug(
        "Configure server security using '{}' auth with {} endpoints", method, endpoints.size());
    method.configure(http);

    http.authorizeExchange(
        auth -> {
          for (Endpoint endpoint : endpoints) {
            method.filter(endpoint, auth.pathMatchers(endpoint.path()));
          }
          auth.anyExchange().permitAll();
        });

    return http.build();
  }

  @Bean
  public ReactiveUserDetailsService userDetailsService() {
    log.info("Creating UserDetailsService using authMethod: {}", authMethod(auth));
    return authMethod(auth).configureUsers();
  }

  private static HttpServerAuthMethod authMethod(AuthMethod auth) {
    var httpServerAuthMethodStream =
        Stream.of(auth.clientCert(), auth.basic(), auth.oauth2(), auth.none())
            .filter(Objects::nonNull)
            .toList();
    if (httpServerAuthMethodStream.size() == 1) {
      return httpServerAuthMethodStream.getFirst();
    } else if (httpServerAuthMethodStream.isEmpty()) {
      return HttpServerNoneAuth.NONE;
    } else {
      throw new IllegalArgumentException(
          "Multiple HTTP Server Auth methods found: " + httpServerAuthMethodStream);
    }
  }

  public record Endpoint(String path, String role) {}
}
