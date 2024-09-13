package care.smith.fts.util.auth;

import care.smith.fts.util.auth.HttpServerAuthConfig.Endpoint;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;

public interface HttpServerAuthMethod {

  ServerHttpSecurity configure(ServerHttpSecurity http);

  default ServerHttpSecurity filter(Endpoint endpoint, ServerHttpSecurity http) {
    return http.authorizeExchange(
        exchange -> exchange.pathMatchers(endpoint.path()).hasRole(endpoint.role()));
  }

  ReactiveUserDetailsService configureUsers();

  record AuthMethod(
      HttpServerClientCertAuth clientCert, HttpServerBasicAuth basic, HttpServerNoneAuth none) {}

  AuthMethod NONE = new AuthMethod(null, null, HttpServerNoneAuth.NONE);
}
