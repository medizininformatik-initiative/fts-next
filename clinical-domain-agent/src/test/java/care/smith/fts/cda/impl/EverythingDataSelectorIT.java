package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.google.common.net.HttpHeaders.ACCEPT;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.Period;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.services.PatientIdResolver;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class)
@WireMockTest
class EverythingDataSelectorIT extends AbstractConnectionScenarioIT {

  private static WireMock wireMock;
  private static final String PATIENT_ID = "patient-112348";
  private static final int PAGE_SIZE = 500;
  private static final PatientIdResolver pidResolver = pid -> Mono.just(new IdType("Patient", pid));

  private static EverythingDataSelector dataSelector;
  private static WebClient client;
  private static MeterRegistry meterRegistry;
  private static ConsentedPatient consentedPatient;

  @BeforeEach
  void setUp(
      WireMockRuntimeInfo wireMockRuntime,
      @Autowired WebClientFactory clientFactory,
      @Autowired MeterRegistry meterRegistry) {
    wireMock = wireMockRuntime.getWireMock();
    client = clientFactory.create(clientConfig(wireMockRuntime));
    var common = new DataSelector.Config(false, null);
    EverythingDataSelectorIT.meterRegistry = meterRegistry;
    dataSelector =
        new EverythingDataSelector(
            common, client, pidResolver, EverythingDataSelectorIT.meterRegistry, PAGE_SIZE);

    var consentedPolicies = new ConsentedPolicies();
    consentedPolicies.put("pol", new Period(ZonedDateTime.now(), ZonedDateTime.now().plusYears(5)));
    consentedPatient = new ConsentedPatient(PATIENT_ID, consentedPolicies);
  }

  private static MappingBuilder fhirStoreRequestWithoutConsent() {
    return get("/Patient/%s/$everything?_count=%s".formatted(PATIENT_ID, PAGE_SIZE))
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  private static MappingBuilder fhirStoreRequestWithConsent() {
    var period = consentedPatient.consentedPolicies().maxConsentedPeriod().get();
    var start = period.start().format(ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()));
    var end = period.end().format(ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()));
    return get("/Patient/%s/$everything?_count=%s&start=%s&end=%s"
            .formatted(PATIENT_ID, PAGE_SIZE, start, end))
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<ConsentedPatientBundle>() {
      @Override
      public MappingBuilder requestBuilder() {
        return EverythingDataSelectorIT.fhirStoreRequestWithConsent();
      }

      @Override
      public Flux<ConsentedPatientBundle> executeStep() {
        return dataSelector.select(consentedPatient);
      }

      @Override
      public String acceptedContentType() {
        return APPLICATION_FHIR_JSON_VALUE;
      }
    };
  }

  @Test
  void noConsentErrors() {
    create(dataSelector.select(new ConsentedPatient(PATIENT_ID))).expectError().verify();
  }

  @Test
  void noConsentSucceedsIfConsentIgnored() {
    DataSelector.Config common = new DataSelector.Config(true, null);
    var dataSelector = new EverythingDataSelector(common, client, pidResolver, meterRegistry, 500);

    wireMock.register(fhirStoreRequestWithoutConsent().willReturn(fhirResponse(new Bundle())));

    create(dataSelector.select(consentedPatient))
        .assertNext(b -> assertThat(b.consentedPatient().id()).isEqualTo(PATIENT_ID))
        .verifyComplete();
  }

  @Test
  void selectionSucceeds() {
    wireMock.register(fhirStoreRequestWithConsent().willReturn(fhirResponse(new Bundle())));

    create(dataSelector.select(consentedPatient)).expectNextCount(1).verifyComplete();
  }
}
