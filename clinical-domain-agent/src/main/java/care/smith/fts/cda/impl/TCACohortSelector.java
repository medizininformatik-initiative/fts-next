package care.smith.fts.cda.impl;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

class TCACohortSelector implements CohortSelector {
  private final TCACohortSelectorConfig config;
  private final WebClient client;

  public TCACohortSelector(TCACohortSelectorConfig config, WebClient client) {
    this.config = config;
    this.client = client;
  }

  @Override
  public Flux<ConsentedPatient> selectCohort() {
    return client
        .post()
        .uri("/api/v1/cd/consent-request")
        .bodyValue(Map.of("policies", config.policies(), "domain", config.domain()))
        .headers(h -> h.setContentType(APPLICATION_JSON))
        .retrieve()
        .bodyToFlux(new ParameterizedTypeReference<>() {});
  }
}
