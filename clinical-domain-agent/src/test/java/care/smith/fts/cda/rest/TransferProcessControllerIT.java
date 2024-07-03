package care.smith.fts.cda.rest;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.cda.BaseIT;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.TransferProcessRunner.State;
import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerator.Fixed;
import care.smith.fts.test.FhirGenerator.UUID;
import care.smith.fts.test.TestPatientGenerator;
import care.smith.fts.util.FhirUtils;
import care.smith.fts.util.tca.PseudonymizeRequest;
import care.smith.fts.util.tca.PseudonymizeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class, webEnvironment = RANDOM_PORT)
public class TransferProcessControllerIT extends BaseIT {
  private WebClient client;

  ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

  @BeforeEach
  void setUp(@LocalServerPort int port) {
    client = WebClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void successfulRequest() throws IOException {

    String patientId = randomUUID().toString();
    var identifierSystem = "http://fts.smith.care";

    FhirGenerator gicsConsentGenerator = new FhirGenerator("GicsResponseTemplate.json");
    gicsConsentGenerator.replaceTemplateFieldWith("$QUESTIONNAIRE_RESPONSE_ID", new UUID());
    gicsConsentGenerator.replaceTemplateFieldWith("$PATIENT_ID", new Fixed(patientId));
    var consent = gicsConsentGenerator.generateBundle(1, 1);
    tca.when(request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(APPLICATION_JSON)
                .withBody(FhirUtils.fhirResourceToString(consent)));

    var tid1 = patientId + ".identifier." + identifierSystem + ":" + patientId;
    var tid2 = patientId + ".id.Patient:" + patientId;

    var pseudonymizeRequest =
        new PseudonymizeRequest(patientId, Set.of(tid1, tid2), "MII", Duration.ofDays(14));
    PseudonymizeResponse pseudonymizeResponse =
        new PseudonymizeResponse(Map.of(tid1, "tid1", tid2, "tid2"), Duration.ofDays(1));
    HttpRequest requestDefinition1 =
        request()
            .withMethod("POST")
            .withContentType(APPLICATION_JSON)
            .withPath("/api/v2/cd/transport-ids-and-date-shifting-values")
            .withBody(json(om.writeValueAsString(pseudonymizeRequest)));
    log.info("{}", requestDefinition1);
    tca.when(requestDefinition1)
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(APPLICATION_JSON)
                .withBody(om.writeValueAsString(pseudonymizeResponse)));

    var patient = TestPatientGenerator.generateOnePatient(patientId, "2025", identifierSystem);

    var fhirResolveGen = new FhirGenerator("FhirResolveSearchRequestTemplate.json");
    fhirResolveGen.replaceTemplateFieldWith("$PATIENT_ID", new Fixed(patientId));
    fhirResolveGen.replaceTemplateFieldWith("$HDS_ID", new UUID());

    hds.when(
            request()
                .withMethod("GET")
                .withHeader("accept", APPLICATION_FHIR_JSON_VALUE)
                .withPath("/Patient")
                .withQueryStringParameter("identifier", identifierSystem + "|" + patientId))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.parse(APPLICATION_FHIR_JSON_VALUE))
                .withBody(
                    FhirUtils.fhirResourceToString(
                        fhirResolveGen.generateBundle(1, 1).getEntryFirstRep().getResource())));

    hds.when(
            request()
                .withMethod("GET")
                .withHeader("accept", APPLICATION_FHIR_JSON_VALUE)
                .withPath("/Patient/%s/$everything".formatted(patientId))
                .withQueryStringParameter("start", "2023-07-29")
                .withQueryStringParameter("end", "2028-07-29"))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.parse(APPLICATION_FHIR_JSON_VALUE))
                .withBody(FhirUtils.fhirResourceToString(patient)));

    rda.when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/test/patient")
                .withBody(
                    json(
                        "{\"resourceType\":\"Bundle\",\"total\":2}",
                        MatchType.ONLY_MATCHING_FIELDS)))
        .respond(response());

    StepVerifier.create(
            client
                .post()
                .uri("/api/v2/process/test/start")
                .retrieve()
                .toBodilessEntity()
                .mapNotNull(r -> r.getHeaders().get("Content-Location"))
                .doOnNext(r -> assertThat(r).isNotEmpty())
                .doOnNext(r -> assertThat(r.getFirst()).contains("/api/v2/process/status/"))
                .flatMap(r -> client.get().uri(r.getFirst()).retrieve().bodyToMono(State.class))
                .doOnNext(e -> log.info("Result: {}", e))
                .doOnError(e -> log.info("Error", e)))
        .expectNextCount(1)
        .verifyComplete();
  }
}

// Wrong domain ret from gics
// {
//  "resourceType": "OperationOutcome",
//  "issue": [
//    {
//      "severity": "error",
//      "code": "processing",
//      "diagnostics": "No consents found for domain  'MII333'."
//    }
//  ]
// }
