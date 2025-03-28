package care.smith.fts.cda.auth;

import care.smith.fts.test.AbstractAuthIT;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;

public class AuthIT {

  @ActiveProfiles("auth:basic")
  static class BasicAuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }

  @ActiveProfiles("auth:oauth2")
  static class OAuth2AuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }

  @ActiveProfiles("auth:cert")
  static class CertAuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }
}
