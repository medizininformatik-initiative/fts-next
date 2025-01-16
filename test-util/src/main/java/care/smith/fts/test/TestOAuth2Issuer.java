package care.smith.fts.test;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Profile;

@Profile("auth:oauth2")
@TestComponent
public class TestOAuth2Issuer {

  KeycloakContainer keycloak = new KeycloakContainer();
}
