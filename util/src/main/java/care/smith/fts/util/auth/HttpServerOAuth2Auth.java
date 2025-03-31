package care.smith.fts.util.auth;

import static org.springframework.security.oauth2.jwt.ReactiveJwtDecoders.fromIssuerLocation;

import care.smith.fts.util.auth.HttpServerAuthConfig.Endpoint;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec.Access;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

public record HttpServerOAuth2Auth(String issuer) implements HttpServerAuthMethod {

  ReactiveJwtDecoder createJwtDecoder() {
    return fromIssuerLocation(issuer);
  }

  @Override
  public ServerHttpSecurity configure(ServerHttpSecurity http) {
    return http.oauth2ResourceServer(
        oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(createJwtDecoder())));
  }

  @Override
  public AuthorizeExchangeSpec filter(Endpoint endpoint, Access access) {
    return access.authenticated();
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
