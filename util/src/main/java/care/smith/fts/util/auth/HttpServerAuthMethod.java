package care.smith.fts.util.auth;

import care.smith.fts.util.auth.HttpServerAuthConfig.Endpoint;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec.Access;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;

public interface HttpServerAuthMethod {

  ServerHttpSecurity configure(ServerHttpSecurity http);

  default AuthorizeExchangeSpec filter(Endpoint endpoint, Access access) {
    return access.hasRole(endpoint.role());
  }

  ReactiveUserDetailsService configureUsers();

  record AuthMethod(
      HttpServerClientCertAuth clientCert,
      HttpServerBasicAuth basic,
      HttpServerOAuth2Auth oauth2,
      HttpServerNoneAuth none) {}

  AuthMethod NONE = new AuthMethod(null, null, null, HttpServerNoneAuth.NONE);
}
