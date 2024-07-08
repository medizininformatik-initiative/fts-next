package care.smith.fts.cda.impl;

import static care.smith.fts.util.ConsentedPatientExtractor.extractConsentedPatients;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.error.TransferProcessException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
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
        .onStatus(
            r -> r.equals(HttpStatus.BAD_REQUEST),
            r ->
                r.bodyToMono(ProblemDetail.class)
                    .flatMap(b -> Mono.error(new TransferProcessException(b.getDetail()))))
        .bodyToMono(Bundle.class)
        // TODO Paging using .expand()? see Flare
        .doOnNext(b -> log.debug("Found {} consented patient bundles", b.getEntry().size()))
        .onErrorResume(
            WebClientException.class,
            e -> {
              log.error(e.getMessage());
              return Mono.error(
                  new TransferProcessException("Error communicating with trust center agent", e));
            })
        .flatMapMany(
            outerBundle ->
                Flux.fromStream(
                    extractConsentedPatients(
                        config.patientIdentifierSystem(),
                        config.policySystem(),
                        outerBundle,
                        config.policies())));
  }
}
