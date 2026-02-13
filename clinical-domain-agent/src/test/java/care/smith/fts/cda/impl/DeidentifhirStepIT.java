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
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.tca.TcaDomains;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
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
    step = new DeidentifhirStep(client, domains, ofDays(14), NONE, config, meterRegistry);

    var bundle = generateOnePatient("id1", "2024", "identifierSystem", "identifier1");
    var consentedPatient = new ConsentedPatient("id1", "system");
    consentedPatientBundle = new ConsentedPatientBundle(bundle, consentedPatient);
  }

  private static MappingBuilder transportMappingRequest() {
    return post("/api/v2/cd/transport-mapping")
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
                      "transferId": "transferId"
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

    var requests = wireMock.findAll(postRequestedFor(urlEqualTo("/api/v2/cd/transport-mapping")));
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
            meterRegistry);

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
                    {"transferId": "date-only-transfer"}
                    """)));

    create(dateOnlyStep.deidentify(cpb))
        .assertNext(tb -> assertThat(tb.transferId()).isEqualTo("date-only-transfer"))
        .verifyComplete();
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
