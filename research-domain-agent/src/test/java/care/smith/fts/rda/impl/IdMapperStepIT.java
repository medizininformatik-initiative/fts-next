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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
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
class IdMapperStepIT extends AbstractConnectionScenarioIT {

  @Autowired MeterRegistry meterRegistry;
  private WireMock wireMock;

  private IdMapperStep step;
  private Bundle bundle;

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<Bundle>() {
      @Override
      public MappingBuilder requestBuilder() {
        return IdMapperStepIT.secureMappingRequest();
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
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    step = new IdMapperStep(client, meterRegistry);
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
  void emptyTCAResponseCompletes() {
    wireMock.register(WireMock.post(urlPathEqualTo("/api/v2/rd/secure-mapping")).willReturn(ok()));

    create(step.deidentify(new TransportBundle(bundle, "transferId"))).verifyComplete();
  }

  @Test
  void deidentifyReplacesResourceIds() {
    var patient = new Patient();
    patient.setId("Patient/tid1");
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {"tid1": "pid1"}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              assertThat(inner.getEntryFirstRep().getResource().getIdPart()).isEqualTo("pid1");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyReplacesReferences() {
    var observation = new Observation();
    observation.setSubject(new Reference("Patient/tid1"));
    var testBundle = wrapInOuterBundle(observation);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {"tid1": "pid1"}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var obs = (Observation) inner.getEntryFirstRep().getResource();
              assertThat(obs.getSubject().getReference()).isEqualTo("Patient/pid1");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyReplacesIdentifierValues() {
    var patient = new Patient();
    patient.addIdentifier().setSystem("http://example.org").setValue("tidentifier1");
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {"tidentifier1": "pidentifier1"}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getIdentifierFirstRep().getValue()).isEqualTo("pidentifier1");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyRestoresShiftedDates() {
    var patient = new Patient();
    var birthDate = new DateType("2000-01-01");
    birthDate.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-birthDate"));
    patient.setBirthDateElement(birthDate);
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {"tId-birthDate": "2000-01-15"}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-15");
              assertThat(p.getBirthDateElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL))
                  .isNull();
            })
        .verifyComplete();
  }

  @Test
  void deidentifyRestoresNestedShiftedDates() {
    var observation = new Observation();
    var period = new Period();
    var start = new DateTimeType("2024-01-01T08:00:00Z");
    start.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-start"));
    var end = new DateTimeType("2024-01-01T12:00:00Z");
    end.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("tId-end"));
    period.setStartElement(start);
    period.setEndElement(end);
    observation.setEffective(period);
    var testBundle = wrapInOuterBundle(observation);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {},
                     "dateShiftMap": {"tId-start": "2024-01-05T08:00:00Z",
                                      "tId-end": "2024-01-05T12:00:00Z"}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var obs = (Observation) inner.getEntryFirstRep().getResource();
              var p = (Period) obs.getEffective();
              assertThat(p.getStartElement().getValueAsString()).isEqualTo("2024-01-05T08:00:00Z");
              assertThat(p.getEndElement().getValueAsString()).isEqualTo("2024-01-05T12:00:00Z");
              assertThat(p.getStartElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
              assertThat(p.getEndElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL)).isNull();
            })
        .verifyComplete();
  }

  @Test
  void deidentifyHandlesReferenceWithoutSlash() {
    var observation = new Observation();
    observation.setSubject(new Reference("urn:uuid:some-id"));
    var testBundle = wrapInOuterBundle(observation);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {"urn:uuid:some-id": "resolved"}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var obs = (Observation) inner.getEntryFirstRep().getResource();
              assertThat(obs.getSubject().getReference())
                  .as("Reference without '/' should remain unchanged")
                  .isEqualTo("urn:uuid:some-id");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyHandlesIdentifierWithNullValue() {
    var patient = new Patient();
    patient.addIdentifier().setSystem("http://example.org");
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getIdentifierFirstRep().getValue())
                  .as("Identifier with null value should remain null")
                  .isNull();
            })
        .verifyComplete();
  }

  @Test
  void deidentifyHandlesReferenceWithNullValue() {
    var observation = new Observation();
    observation.setSubject(new Reference().setDisplay("Some Patient"));
    var testBundle = wrapInOuterBundle(observation);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var obs = (Observation) inner.getEntryFirstRep().getResource();
              assertThat(obs.getSubject().getReference())
                  .as("Reference with null value should remain null")
                  .isNull();
              assertThat(obs.getSubject().getDisplay()).isEqualTo("Some Patient");
            })
        .verifyComplete();
  }

  @Test
  void deidentifySkipsUnknownDateShiftKey() {
    var patient = new Patient();
    var birthDate = new DateType("2000-01-01");
    birthDate.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType("unknown-key"));
    patient.setBirthDateElement(birthDate);
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-01");
              assertThat(p.getBirthDateElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL))
                  .isNull();
            })
        .verifyComplete();
  }

  @Test
  void deidentifySkipsNullResourceEntries() {
    var patient = new Patient();
    patient.setId("Patient/tid1");
    var innerBundle = new Bundle();
    innerBundle.addEntry().setResource(patient);
    innerBundle.addEntry(); // entry with null resource
    var outerBundle = new Bundle();
    outerBundle.addEntry().setResource(innerBundle);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {"tid1": "pid1"}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(outerBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              assertThat(inner.getEntry()).hasSize(2);
              assertThat(inner.getEntry().get(0).getResource().getIdPart()).isEqualTo("pid1");
              assertThat(inner.getEntry().get(1).getResource()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void deidentifyProcessesThreeLevelNestedBundles() {
    var patient = new Patient();
    patient.setId("Patient/tid1");
    var innermost = new Bundle();
    innermost.addEntry().setResource(patient);
    var middle = new Bundle();
    middle.addEntry().setResource(innermost);
    var outer = new Bundle();
    outer.addEntry().setResource(middle);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {"tid1": "pid1"}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(outer, "transferId")))
        .assertNext(
            b -> {
              Bundle mid = (Bundle) b.getEntryFirstRep().getResource();
              Bundle inn = (Bundle) mid.getEntryFirstRep().getResource();
              assertThat(inn.getEntryFirstRep().getResource().getIdPart()).isEqualTo("pid1");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyPreservesResourceTypeInId() {
    var patient = new Patient();
    patient.setId("Patient/tid1");
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {"tid1": "pid1"}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = inner.getEntryFirstRep().getResource();
              assertThat(p.getIdElement().getValue()).isEqualTo("Patient/pid1");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyLeavesUnmappedResourceIdUnchanged() {
    var patient = new Patient();
    patient.setId("Patient/unmapped-tid");
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              assertThat(inner.getEntryFirstRep().getResource().getIdPart())
                  .isEqualTo("unmapped-tid");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyLeavesUnmappedReferenceUnchanged() {
    var observation = new Observation();
    observation.setSubject(new Reference("Patient/unmapped-ref"));
    var testBundle = wrapInOuterBundle(observation);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var obs = (Observation) inner.getEntryFirstRep().getResource();
              assertThat(obs.getSubject().getReference()).isEqualTo("Patient/unmapped-ref");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyLeavesUnmappedIdentifierValueUnchanged() {
    var patient = new Patient();
    patient.addIdentifier().setSystem("http://example.org").setValue("unmapped-value");
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getIdentifierFirstRep().getValue()).isEqualTo("unmapped-value");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyIgnoresDateWithoutShiftExtension() {
    var patient = new Patient();
    patient.setBirthDateElement(new DateType("2000-06-15"));
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getBirthDateElement().getValueAsString()).isEqualTo("2000-06-15");
            })
        .verifyComplete();
  }

  @Test
  void deidentifyHandlesNonStringTypeDateShiftExtension() {
    var patient = new Patient();
    var birthDate = new DateType("2000-01-01");
    birthDate.addExtension(DATE_SHIFT_EXTENSION_URL, new IntegerType(42));
    patient.setBirthDateElement(birthDate);
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-01");
              assertThat(p.getBirthDateElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL))
                  .isNull();
            })
        .verifyComplete();
  }

  @Test
  void deidentifyHandlesNullValueDateShiftExtension() {
    var patient = new Patient();
    var birthDate = new DateType("2000-01-01");
    birthDate.addExtension(new Extension(DATE_SHIFT_EXTENSION_URL));
    patient.setBirthDateElement(birthDate);
    var testBundle = wrapInOuterBundle(patient);

    wireMock.register(
        secureMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {"tidPidMap": {}, "dateShiftMap": {}}
                    """)));

    create(step.deidentify(new TransportBundle(testBundle, "transferId")))
        .assertNext(
            b -> {
              Bundle inner = (Bundle) b.getEntryFirstRep().getResource();
              var p = (Patient) inner.getEntryFirstRep().getResource();
              assertThat(p.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-01");
              assertThat(p.getBirthDateElement().getExtensionByUrl(DATE_SHIFT_EXTENSION_URL))
                  .isNull();
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
