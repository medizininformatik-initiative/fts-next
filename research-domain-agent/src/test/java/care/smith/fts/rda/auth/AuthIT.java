package care.smith.fts.rda.auth;

import care.smith.fts.test.AbstractAuthIT;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;

public class AuthIT {

  @Nested
  @ActiveProfiles("auth:basic")
  class BasicAuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }

  @Nested
  @ActiveProfiles("auth:oauth2")
  class OAuth2AuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }

  @Nested
  @ActiveProfiles("auth:cert")
  class CertAuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }
}
