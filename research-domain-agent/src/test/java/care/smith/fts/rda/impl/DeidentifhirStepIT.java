package care.smith.fts.rda.impl;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

@SpringBootTest
@WireMockTest
class DeidentifhirStepIT extends AbstractConnectionScenarioIT {

  private static final String MII_PATIENT_PROFILE =
      "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient";

  @Autowired MeterRegistry meterRegistry;
  private WireMock wireMock;

  private DeidentifhirStep step;
  private Bundle bundle;

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<Bundle>() {
      @Override
      public MappingBuilder requestBuilder() {
        return DeidentifhirStepIT.secureMappingRequest();
      }

      @Override
      public Mono<Bundle> executeStep() {
        return step.deidentify(new TransportBundle(bundle, "transferId"));
      }

      @Override
      public String acceptedContentType() {
        return APPLICATION_FHIR_JSON;
      }
    };
  }

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory)
      throws IOException {
    var config = parseResources(DeidentifhirUtil.class, "TransportToRD.profile");
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    step =
        new DeidentifhirStep(
            config, client, meterRegistry, new DefaultRetryStrategy(meterRegistry));
    wireMock = wireMockRuntime.getWireMock();
    bundle = generateOnePatient("tid1", "2024", "identifierSystem", "tidentifier1");
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  private static MappingBuilder secureMappingRequest() {
    return post("/api/v2/rd/secure-mapping")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
  }

  @Test
  void correctRequestSent() {
    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/secure-mapping"))
            .withRequestBody(equalTo("transferId"))
            .willReturn(ok()));

    create(step.deidentify(new TransportBundle(bundle, "transferId"))).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult() {
    wireMock.register(WireMock.post(urlPathEqualTo("/api/v2/rd/secure-mapping")).willReturn(ok()));

    create(step.deidentify(new TransportBundle(bundle, "transferId"))).verifyComplete();
  }

  @Test
  void deidentifyRestoresShiftedDates() {
    var patient = new Patient();
    patient.getMeta().addProfile(MII_PATIENT_PROFILE);
    // The CDA nulls the date value and puts the tID in the extension, see DeidentifhirUtils.
    var birthDate = new DateType();
    birthDate.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-birthDate"));
    patient.setBirthDateElement(birthDate);

    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/secure-mapping"))
            .willReturn(
                jsonResponse(
                    """
                    {
                      "tidPidMap": {},
                      "dateShiftMap": {"tId-birthDate": "2000-01-15"}
                    }
                    """)));

    create(step.deidentify(new TransportBundle(wrapInOuterBundle(patient), "transferId")))
        .assertNext(
            b -> {
              var inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-15");
            })
        .verifyComplete();
  }

  @Test
  void deidentifySucceeds() {
    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/secure-mapping"))
            .withRequestBody(equalTo("transferId"))
            .willReturn(
                jsonResponse(
                    """
                    {
                      "tidPidMap": {"tid1": "pid1", "tidentifier1": "pidentifier1"},
                      "dateShiftMap": {"2024": "2025"}
                    }
                    """)));

    create(step.deidentify(new TransportBundle(bundle, "transferId")))
        .assertNext(
            b -> {
              assertThat(b.getEntry().size()).isEqualTo(1);
              assertThat(b.getEntryFirstRep()).isNotNull();

              Bundle innerBundle = (Bundle) b.getEntryFirstRep().getResource();
              Resource resource = innerBundle.getEntryFirstRep().getResource();
              assertThat(resource.getIdPart()).isEqualTo("pid1");
            })
        .verifyComplete();
  }

  private static Bundle wrapInOuterBundle(Resource... resources) {
    var innerBundle = new Bundle();
    for (var resource : resources) {
      innerBundle.addEntry().setResource(resource);
    }
    var outerBundle = new Bundle();
    outerBundle.addEntry().setResource(innerBundle);
    return outerBundle;
  }
}
