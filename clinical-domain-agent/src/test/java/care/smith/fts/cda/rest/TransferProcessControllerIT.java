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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/*
 * CDA has two endpoints to test: `/{project}/start` and `/status/{projectId}`.
 * The URL of the latter is part of the response of the first.
 *
 * The `start` endpoint does:
 * 1. Request ConsentedPatients from TCA
 *   a. Filter ConsentedPatients by date range
 * 2. Request FhirId from HDS
 * 3. Request everything from HDS
 * 4. Request transport ids from TCA
 *   a. Deidentify Patient
 * 5. Send Patient to RDA
 *
 * Things that can go wrong:
 * 1. Invalid project
 * 2. CohortSelector
 *   a. TCA slow or down
 *   b. Wrong content type
 *   c. TCA/gICS: unknown domain, this is the only setting that returns an error (bad request),
 * other settings may return an empty bundle
 * 3. DataSelector
 *   a. HDS slow or down
 *   b. FhirResolveService
 *     - wrong content type
 *     - may return error:
 *       - More than one result
 *       - Unable to resolve patient id
 *   c. everything
 *     - wrong content type
 *     - paging - not implemented
 * 4. Deidentifhir
 *   a. TCA slow or down
 *   b. gPAS unknown domain -> bad request
 * 5. BundleSender
 *   a. RDA slow or down
 */
@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class, webEnvironment = RANDOM_PORT)
public class TransferProcessControllerIT extends BaseIT {
  private WebClient client;

  ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

  @BeforeEach
  void setUp(@LocalServerPort int port) {
    client = WebClient.builder().baseUrl("http://localhost:" + port).build();
  }

  static class ITCohortSelector {
    static void success(String patientId) throws IOException {
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
    }
  }

  static class ITDataSelector {
    static class TransportIds {
      static void success(ObjectMapper om, String patientId, String identifierSystem)
          throws JsonProcessingException {
        var tid1 = patientId + ".identifier." + identifierSystem + ":" + patientId;
        var tid2 = patientId + ".id.Patient:" + patientId;

        var pseudonymizeRequest =
            new PseudonymizeRequest(patientId, Set.of(tid1, tid2), "MII", Duration.ofDays(14));
        PseudonymizeResponse pseudonymizeResponse =
            new PseudonymizeResponse(Map.of(tid1, "tid1", tid2, "tid2"), Duration.ofDays(1));
        tca.when(
                request()
                    .withMethod("POST")
                    .withContentType(APPLICATION_JSON)
                    .withPath("/api/v2/cd/transport-ids-and-date-shifting-values")
                    .withBody(json(om.writeValueAsString(pseudonymizeRequest))))
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(APPLICATION_JSON)
                    .withBody(om.writeValueAsString(pseudonymizeResponse)));
      }
    }

    static class FhirResolveService {
      private static void success(String patientId, String identifierSystem) throws IOException {
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
      }
    }

    static class FetchData {
      private static void success(String patientId, Bundle patient) {
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
      }
    }
  }

  static class ITBundleSender {

    private static void success() {
      rda.when(
              request()
                  .withMethod("POST")
                  .withPath("/api/v2/test/patient")
                  .withBody(
                      json(
                          "{\"resourceType\":\"Bundle\",\"total\":2}",
                          MatchType.ONLY_MATCHING_FIELDS)))
          .respond(response());
    }
  }

  @Test
  void successfulRequest() throws IOException {

    String patientId = randomUUID().toString();
    var identifierSystem = "http://fts.smith.care";
    var patient = TestPatientGenerator.generateOnePatient(patientId, "2025", identifierSystem);

    ITCohortSelector.success(patientId);
    ITDataSelector.TransportIds.success(om, patientId, identifierSystem);
    ITDataSelector.FhirResolveService.success(patientId, identifierSystem);
    ITDataSelector.FetchData.success(patientId, patient);

    ITBundleSender.success();

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

  @Test
  void invalidProject() {
    StepVerifier.create(
            client
                .post()
                .uri("/api/v2/process/non-existent/start")
                .retrieve()
                .onStatus(
                    r -> r.equals(HttpStatus.resolve(500)),
                    (c) ->
                        c.bodyToMono(ProblemDetail.class)
                            .flatMap(p -> Mono.error(new IllegalStateException(p.getDetail()))))
                .bodyToMono(String.class))
        .expectErrorMessage("Project non-existent could not be found")
        .verifyThenAssertThat()
        .hasOperatorErrors();
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
