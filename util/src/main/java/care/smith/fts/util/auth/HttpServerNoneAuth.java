package care.smith.fts.util.auth;

import care.smith.fts.util.auth.HttpServerAuthConfig.Endpoint;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;

public class HttpServerNoneAuth implements HttpServerAuthMethod {
  public static HttpServerNoneAuth NONE = new HttpServerNoneAuth();

  @Override
  public ServerHttpSecurity configure(ServerHttpSecurity http) {
    return http;
  }

  @Override
  public ServerHttpSecurity filter(Endpoint endpoint, ServerHttpSecurity http) {
    return http;
  }

  @Override
  public ReactiveUserDetailsService configureUsers() {
    return null;
  }

  @Override
  public String toString() {
    return "None";
  }
}
