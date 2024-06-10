package care.smith.fts.cda.impl;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirService;
import care.smith.fts.cda.services.deidentifhir.IDATScraper;
import care.smith.fts.util.tca.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.hl7.fhir.r4.model.Resource;

class DeidentifhirDeidentificationProvider implements DeidentificationProvider<Resource> {
  private final CloseableHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String domain;
  private final Duration dateShift;
  private final com.typesafe.config.Config deidentifhirConfig;
  private final com.typesafe.config.Config scraperConfig;

  public DeidentifhirDeidentificationProvider(
      File deidentifhirConfigFile,
      File scraperConfigFile,
      CloseableHttpClient httpClient,
      ObjectMapper objectMapper,
      String domain,
      Duration dateShift) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.domain = domain;
    this.dateShift = dateShift;
    this.deidentifhirConfig = ConfigFactory.parseFile(deidentifhirConfigFile);
    this.scraperConfig = ConfigFactory.parseFile(scraperConfigFile);
  }

  @Override
  public Resource deidentify(Resource resource, ConsentedPatient patient) throws IOException {

    IDATScraper idatScraper = new IDATScraper(scraperConfig, patient);
    var ids = idatScraper.gatherIDs(resource);

    PseudonymizeResponse pseudonymizeResponse = fetchTransportIdsAndDateShiftingValues(ids);
    TransportIDs transportIDs = pseudonymizeResponse.getTransportIDs();
    Duration dateShiftValue = pseudonymizeResponse.getDateShiftValue();

    DeidentifhirService deidentifhir =
        new DeidentifhirService(deidentifhirConfig, patient, transportIDs, dateShiftValue);

    return deidentifhir.deidentify(resource);
  }

  private PseudonymizeResponse fetchTransportIdsAndDateShiftingValues(Set<String> ids)
      throws IOException {
    var post = new HttpPost("/cd/transport-ids-and-date-shifting-values");
    post.setHeader("Content-Type", "application/json");

    PseudonymizeRequest request = new PseudonymizeRequest();
    request.setIds(ids);
    request.setDomain(domain);
    request.setDateShift(dateShift);
    post.setEntity(new StringEntity(objectMapper.writeValueAsString(request)));

    var response = httpClient.execute(post, r -> r.getEntity().getContent());
    return objectMapper.readValue(response, PseudonymizeResponse.class);
  }
}
