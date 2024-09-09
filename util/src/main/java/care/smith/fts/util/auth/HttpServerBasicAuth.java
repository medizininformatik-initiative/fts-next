package care.smith.fts.util.auth;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.core.userdetails.User.withUsername;

import java.util.List;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;

public record HttpServerBasicAuth(List<UserSpec> users) implements HttpServerAuthMethod {

  @Override
  public ServerHttpSecurity configure(ServerHttpSecurity http) {
    return http
        /* We disable csrf for basic auth */
        .csrf(CsrfSpec::disable)
        .httpBasic(withDefaults());
  }

  @Override
  public ReactiveUserDetailsService configureUsers() {
    var array = users.stream().map(UserSpec::toUserDetails).toArray(UserDetails[]::new);
    return new MapReactiveUserDetailsService(array);
  }

  public record UserSpec(String username, String password, String role) {
    UserDetails toUserDetails() {
      return withUsername(this.username()).password(this.password()).roles(this.role()).build();
    }
  }

  @Override
  public String toString() {
    return "Basic";
  }
}
