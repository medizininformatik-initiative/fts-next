package care.smith.fts.cda.impl;

import static care.smith.fts.util.ConsentedPatientExtractor.extractConsentedPatients;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
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
        .uri("/api/v2/cd/consented-patients")
        .bodyValue(
            Map.of(
                "policies",
                config.policies(),
                "policySystem",
                config.policySystem(),
                "domain",
                config.domain()))
        .headers(h -> h.setContentType(APPLICATION_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .bodyToFlux(Bundle.class)
        .flatMap(
            outerBundle ->
                Flux.fromStream(
                    extractConsentedPatients(
                        config.patientIdentifierSystem(),
                        config.policySystem(),
                        outerBundle,
                        config.policies())));
  }
}
