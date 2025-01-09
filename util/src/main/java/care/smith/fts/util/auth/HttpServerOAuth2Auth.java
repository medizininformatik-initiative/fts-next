package care.smith.fts.util.auth;

import static org.springframework.security.oauth2.jwt.ReactiveJwtDecoders.fromIssuerLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

@Slf4j
public record HttpServerOAuth2Auth(String issuer) implements HttpServerAuthMethod {

  @Override
  public ServerHttpSecurity configure(ServerHttpSecurity http) {
    return http.oauth2ResourceServer(
        oauth2 -> {
          oauth2.jwt(
              jwt -> {
                jwt.jwtDecoder(
                    token ->
                        fromIssuerLocation(issuer)
                            .decode(token)
                            .doOnNext(
                                decodedJwt -> {
                                  log.debug("JWT Headers: {}", decodedJwt.getHeaders());
                                  log.debug("JWT Claims: {}", decodedJwt.getClaims());
                                }));

                jwt.jwtAuthenticationConverter(this::keycloak2spring);
              });
        });
  }

  private Mono<AbstractAuthenticationToken> keycloak2spring(Jwt jwt) {
    var authorities = extractAuthorities(jwt);
    log.debug(
        "Extracted authorities from JWT: {}",
        authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));

    return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("sub")));
  }

  @SuppressWarnings("unchecked")
  private List<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
    return Optional.ofNullable(jwt.getClaimAsMap("resource_access"))
        .map(resourceAccess -> (Map<String, Object>) resourceAccess.get("FTSnext"))
        .map(clientAccess -> (List<String>) clientAccess.get("roles"))
        .map(
            roles ->
                roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList()))
        .orElseGet(
            () -> {
              log.warn("No authorities found in JWT token");
              return Collections.emptyList();
            });
  }

  @Override
  public ReactiveUserDetailsService configureUsers() {
    // Since it's client credentials, we don't configure users explicitly here.
    return null;
  }

  @Override
  public String toString() {
    return "OAuth2";
  }
}
