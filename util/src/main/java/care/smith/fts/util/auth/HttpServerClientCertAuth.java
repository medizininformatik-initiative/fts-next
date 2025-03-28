package care.smith.fts.util.auth;

import static org.springframework.security.core.userdetails.User.withUsername;

import care.smith.fts.util.auth.HttpServerAuthConfig.Endpoint;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec.Access;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;

@Slf4j
public record HttpServerClientCertAuth(List<UserSpec> users) implements HttpServerAuthMethod {

  @Override
  public ServerHttpSecurity configure(ServerHttpSecurity http) {
    return http.x509(x509 -> x509.principalExtractor(HttpServerClientCertAuth::extractCN));
  }

  private static String extractCN(X509Certificate cert) {
    return cert.getSubjectX500Principal().getName().replaceFirst("CN=", "");
  }

  @Override
  public AuthorizeExchangeSpec filter(Endpoint endpoint, Access access) {
    return access.authenticated();
  }

  @Override
  public ReactiveUserDetailsService configureUsers() {
    var array = users.stream().map(UserSpec::toUserDetails).toArray(UserDetails[]::new);
    return new MapReactiveUserDetailsService(array);
  }

  public record UserSpec(String username, String role) {
    UserDetails toUserDetails() {
      return withUsername(this.username()).password("").roles(this.role()).build();
    }
  }

  @Override
  public String toString() {
    return "Client Cert";
  }
}
