package care.smith.fts.cda.impl;

import static care.smith.fts.api.DateShiftPreserve.NONE;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.typesafe.config.ConfigFactory.parseResources;
import static com.typesafe.config.ConfigFactory.parseString;
import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.ProblemDetail.forStatusAndDetail;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.tca.TcaDomains;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = ClinicalDomainAgent.class)
@WireMockTest
class DeidentifhirStepIT extends AbstractConnectionScenarioIT {

  private DeidentifhirStep step;
  private WireMock wireMock;
  private ConsentedPatientBundle consentedPatientBundle;

  @BeforeEach
  void setUp(
      WireMockRuntimeInfo wireMockRuntime,
      @Autowired WebClientFactory clientFactory,
      @Autowired MeterRegistry meterRegistry)
      throws IOException {
    var config = parseResources(DeidentifhirUtils.class, "CDtoTransport.profile");
    var domains = new TcaDomains("domain", "domain", "domain");
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    wireMock = wireMockRuntime.getWireMock();
    step =
        new DeidentifhirStep(
            client,
            domains,
            ofDays(14),
            NONE,
            config,
            meterRegistry,
            new DefaultRetryStrategy(meterRegistry));

    var bundle = generateOnePatient("id1", "2024", "identifierSystem", "identifier1");
    var consentedPatient = new ConsentedPatient("id1", "system");
    consentedPatientBundle = new ConsentedPatientBundle(bundle, consentedPatient);
  }

  private static MappingBuilder transportMappingRequest() {
    return post("/api/v2/cd/transport-mappings")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
  }

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<TransportBundle>() {
      @Override
      public MappingBuilder requestBuilder() {
        return DeidentifhirStepIT.transportMappingRequest();
      }

      @Override
      public Mono<TransportBundle> executeStep() {
        return step.deidentify(consentedPatientBundle);
      }

      @Override
      public String acceptedContentType() {
        return MimeTypeUtils.APPLICATION_JSON_VALUE;
      }
    };
  }

  @Test
  void correctRequestSent() {
    wireMock.register(transportMappingRequest().willReturn(ok()));

    create(step.deidentify(consentedPatientBundle)).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult() {
    wireMock.register(transportMappingRequest().willReturn(ok()));

    create(step.deidentify(consentedPatientBundle)).verifyComplete();
  }

  @Test
  void deidentifySucceeds() {
    wireMock.register(
        transportMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {
                      "transferIds": ["transferId"]
                    }
                    """)));

    create(step.deidentify(consentedPatientBundle))
        .assertNext(
            transportBundle -> {
              assertThat(transportBundle.transferId()).isEqualTo("transferId");

              var outerBundle = transportBundle.bundle();
              assertThat(outerBundle.getEntry()).isNotEmpty();

              // Deidentified bundle is nested: outer Bundle → inner Bundle → resources
              var innerBundle = (Bundle) outerBundle.getEntryFirstRep().getResource();
              var patient = innerBundle.getEntryFirstRep().getResource();
              assertThat(patient.getIdElement().getIdPart()).hasSize(21);
            })
        .verifyComplete();

    var requests = wireMock.findAll(postRequestedFor(urlEqualTo("/api/v2/cd/transport-mappings")));
    assertThat(requests).hasSize(1);

    var body = requests.getFirst().getBodyAsString();
    assertThat(body).contains("\"patientIdentifier\"");
    assertThat(body).contains("\"idMappings\"");
  }

  @Test
  void emptyIdsYieldEmptyMono() {
    create(
            step.deidentify(
                new ConsentedPatientBundle(new Bundle(), new ConsentedPatient("id1", "system"))))
        .expectNextCount(0)
        .verifyComplete();
  }

  @Test
  void onlyDateMappingsSendsMappingsToTca(
      WireMockRuntimeInfo wireMockRuntime,
      @Autowired WebClientFactory clientFactory,
      @Autowired MeterRegistry meterRegistry) {
    var dateOnlyConfig =
        parseString(
"""
{
  deidentiFHIR.profile.version=0.2
  modules {
    test_patient {
      base = ["Patient.id", "Patient.birthDate", "Patient.meta.profile"]
      paths { "Patient.birthDate" { handler = shiftDateHandler } }
      pattern = "Patient.meta.profile contains 'https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient'"
    }
  }
}
""");

    var client = clientFactory.create(clientConfig(wireMockRuntime));
    var dateOnlyStep =
        new DeidentifhirStep(
            client,
            new TcaDomains("domain", "domain", "domain"),
            ofDays(14),
            NONE,
            dateOnlyConfig,
            meterRegistry,
            new DefaultRetryStrategy(meterRegistry));

    var patient = new Patient();
    patient.setId("test-patient");
    patient
        .getMeta()
        .addProfile(
            "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient");
    patient.setBirthDateElement(new DateType("1990-01-01"));

    var innerBundle = new Bundle();
    innerBundle.addEntry().setResource(patient);

    var outerBundle = new Bundle();
    outerBundle.addEntry().setResource(innerBundle);
    outerBundle.setTotal(1);

    var cpb =
        new ConsentedPatientBundle(outerBundle, new ConsentedPatient("test-patient", "system"));

    wireMock.register(
        transportMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"transferIds": ["date-only-transfer"]}
                    """)));

    create(dateOnlyStep.deidentify(cpb))
        .assertNext(tb -> assertThat(tb.transferId()).isEqualTo("date-only-transfer"))
        .verifyComplete();
  }

  @Test
  void deidentifyBatchSendsSingleTcaRequest() throws IOException {
    wireMock.register(
        transportMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"transferIds": ["t1", "t2"]}
                    """)));

    var secondBundle =
        new ConsentedPatientBundle(
            generateOnePatient("id2", "2024", "identifierSystem", "identifier2"),
            new ConsentedPatient("id2", "system"));

    create(step.deidentify(List.of(consentedPatientBundle, secondBundle)).collectList())
        .assertNext(
            results -> {
              assertThat(results)
                  .hasSize(2)
                  .allMatch(r -> r instanceof Deidentificator.DeidentificationResult.Success);
              assertThat(results)
                  .extracting(
                      r ->
                          ((Deidentificator.DeidentificationResult.Success) r)
                              .bundle()
                              .transferId())
                  .containsExactlyInAnyOrder("t1", "t2");
            })
        .verifyComplete();

    // Both patients are sent to the TCA in a single request, not one request per patient.
    var requests = wireMock.findAll(postRequestedFor(urlEqualTo("/api/v2/cd/transport-mappings")));
    assertThat(requests).hasSize(1);
  }

  @Test
  void localFailureIsIsolatedFromTheRestOfTheBatch() {
    wireMock.register(
        transportMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"transferIds": ["transferId"]}
                    """)));

    // A null input bundle makes the local deidentification throw for this patient only.
    var failing = new ConsentedPatientBundle(null, new ConsentedPatient("bad", "system"));

    create(step.deidentify(List.of(consentedPatientBundle, failing)).collectList())
        .assertNext(
            results -> {
              assertThat(results).hasSize(2);
              assertThat(results)
                  .filteredOn(r -> r instanceof Deidentificator.DeidentificationResult.Success)
                  .hasSize(1);
              assertThat(results)
                  .filteredOn(r -> r instanceof Deidentificator.DeidentificationResult.Failure)
                  .hasSize(1);
            })
        .verifyComplete();
  }

  @Test
  void singleLocalFailurePropagatesError() {
    var failing = new ConsentedPatientBundle(null, new ConsentedPatient("bad", "system"));
    create(step.deidentify(failing)).expectError(NullPointerException.class).verify();
  }

  @Test
  void handleBadRequest() {
    var response = jsonResponse(forStatusAndDetail(BAD_REQUEST, "TCA Returns Bad Request"));
    wireMock.register(transportMappingRequest().willReturn(response));
    create(step.deidentify(consentedPatientBundle))
        .expectErrorMessage("TCA Returns Bad Request")
        .verify();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
