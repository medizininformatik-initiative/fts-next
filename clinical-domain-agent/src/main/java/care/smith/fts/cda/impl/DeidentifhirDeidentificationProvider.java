package care.smith.fts.cda.impl;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirService;
import care.smith.fts.cda.services.deidentifhir.IDATScraper;
import care.smith.fts.util.tca.*;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.time.Duration;
import java.util.Set;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DeidentifhirDeidentificationProvider implements DeidentificationProvider<Resource> {
  private final WebClient httpClient;
  private final String domain;
  private final Duration dateShift;
  private final com.typesafe.config.Config deidentifhirConfig;
  private final com.typesafe.config.Config scraperConfig;

  public DeidentifhirDeidentificationProvider(
      File deidentifhirConfigFile,
      File scraperConfigFile,
      WebClient httpClient,
      String domain,
      Duration dateShift) {
    this.httpClient = httpClient;
    this.domain = domain;
    this.dateShift = dateShift;
    this.deidentifhirConfig = ConfigFactory.parseFile(deidentifhirConfigFile);
    this.scraperConfig = ConfigFactory.parseFile(scraperConfigFile);
  }

  @Override
  public Flux<Resource> deidentify(Flux<Resource> resourceFlux, ConsentedPatient patient) {
    record Tuple(Resource resource, PseudonymizeResponse response) {}
    return resourceFlux
        .flatMap(
            resource -> {
              IDATScraper idatScraper = new IDATScraper(scraperConfig, patient);
              var ids = idatScraper.gatherIDs(resource);
              return fetchTransportIdsAndDateShiftingValues(ids)
                  .map(response -> new Tuple(resource, response));
            })
        .map(
            t -> {
              TransportIDs transportIDs = t.response.getTransportIDs();
              Duration dateShiftValue = t.response.getDateShiftValue();

              DeidentifhirService deidentifhir =
                  new DeidentifhirService(
                      deidentifhirConfig, patient, transportIDs, dateShiftValue);

              return deidentifhir.deidentify(t.resource);
            });
  }

  private Mono<PseudonymizeResponse> fetchTransportIdsAndDateShiftingValues(Set<String> ids) {

    PseudonymizeRequest request = new PseudonymizeRequest();
    request.setIds(ids);
    request.setDomain(domain);
    request.setDateShift(dateShift);

    return httpClient
        .post()
        .uri("/cd/transport-ids-and-date-shifting-values")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(request)
        .retrieve()
        .bodyToMono(PseudonymizeResponse.class);
  }
}
