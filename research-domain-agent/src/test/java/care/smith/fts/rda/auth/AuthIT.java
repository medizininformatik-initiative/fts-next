package care.smith.fts.rda.auth;

import care.smith.fts.test.AbstractAuthIT;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;

public class AuthIT {

  @Nested
  @ActiveProfiles("auth_basic")
  class BasicAuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }

  @Nested
  @ActiveProfiles("auth_oauth2")
  class OAuth2AuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }

  @Nested
  @ActiveProfiles("auth_cert")
  class CertAuthIT extends AbstractAuthIT {
    @Override
    protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
      return client.get().uri("/api/v2/projects");
    }
  }
}
