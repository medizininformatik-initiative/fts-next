package care.smith.fts.util.auth;

import care.smith.fts.util.auth.HttpClientOAuth2Auth.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient.Builder;

/** OAuth2 Authentication using oauth2 client credentials flow. */
@Slf4j
@Component
@ConditionalOnBean(ReactiveOAuth2AuthorizedClientManager.class)
public class HttpClientOAuth2Auth implements HttpClientAuth<Config> {

  private final ReactiveOAuth2AuthorizedClientManager clientManager;

  public HttpClientOAuth2Auth(ReactiveOAuth2AuthorizedClientManager clientManager) {
    this.clientManager = clientManager;
  }

  public record Config(String registration) {}

  @Override
  public void configure(Config config, Builder builder) {
    log.debug(
        "Configuring oauth2 client, registration '{}', clientManager: {}",
        config.registration(),
        clientManager);
    var filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(clientManager);
    filter.setDefaultClientRegistrationId(config.registration());
    builder.filter(filter);
  }
}