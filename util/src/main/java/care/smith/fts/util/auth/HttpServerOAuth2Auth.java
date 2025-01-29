package care.smith.fts.util.auth;

import static org.springframework.security.oauth2.jwt.ReactiveJwtDecoders.fromIssuerLocation;

import care.smith.fts.util.auth.HttpServerAuthConfig.Endpoint;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Getter
@Slf4j
public class HttpServerOAuth2Auth implements HttpServerAuthMethod {

  private final String issuer;

  public HttpServerOAuth2Auth(String issuer) {
    this.issuer = issuer;
  }

  protected ReactiveJwtDecoder createJwtDecoder() {
    return fromIssuerLocation(issuer);
  }

  @Override
  public ServerHttpSecurity configure(ServerHttpSecurity http) {
    return http.oauth2ResourceServer(
        oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(createJwtDecoder())));
  }

  @Override
  public ServerHttpSecurity filter(Endpoint endpoint, ServerHttpSecurity http) {
    return http.authorizeExchange(
        exchange -> exchange.pathMatchers(endpoint.path()).authenticated());
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
