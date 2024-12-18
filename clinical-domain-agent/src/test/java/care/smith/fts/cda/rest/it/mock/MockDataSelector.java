package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.time.Duration.ofDays;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.springframework.http.HttpHeaders.ACCEPT;

import care.smith.fts.util.tca.TCADomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.Set;

public class MockDataSelector {

  private final ObjectMapper om;
  private final WireMock hds;
  private final WireMock tca;

  public MockDataSelector(ObjectMapper om, WireMockServer tca, WireMockServer hds) {
    this.om = om;
    this.tca = new WireMock(tca);
    this.hds = new WireMock(hds);
  }

  public MockFetchData whenFetchData(String patientId) {
    return MockFetchData.builder()
        .hds(hds)
        .mockRequestSpec(
            get(urlPathEqualTo("/Patient/%s/$everything".formatted(patientId)))
                .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON_VALUE))
                .withQueryParams(
                    ofEntries(
                        entry("start", equalTo("2023-07-29")),
                        entry("end", equalTo("2028-07-29")))))
        .build();
  }

  public MockFhirResolveService whenResolvePatient(String patientId, String identifierSystem) {
    return MockFhirResolveService.builder()
        .hds(hds)
        .mockRequestSpec(
            get(urlPathEqualTo("/Patient"))
                .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON_VALUE))
                .withQueryParam("identifier", equalTo(identifierSystem + "|" + patientId)))
        .build();
  }

  public MockTransportIds whenTransportMapping(String patientId, String identifierSystem)
      throws JsonProcessingException {
    var id1 = patientId + ".identifier." + identifierSystem + ":" + patientId;
    var id2 = patientId + ".Patient:" + patientId;

    var ids = Set.of(id1, id2);
    var tcaDomains = new TCADomains("MII", "MII", "MII");
    var pseudonymizeRequest = new TransportMappingRequest(patientId, ids, tcaDomains, ofDays(14));
    return MockTransportIds.builder()
        .tca(tca)
        .transportIds(ids)
        .om(om)
        .mockRequestSpec(
            post(urlPathEqualTo("/api/v2/cd/transport-mapping"))
                .withRequestBody(
                    equalToJson(om.writeValueAsString(pseudonymizeRequest), true, true)))
        .build();
  }
}
