package care.smith.fts.cda.impl;

import static care.smith.fts.cda.impl.FhirCohortSelectorFactory.consentedPatientExtractor;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.test.MockServerUtil;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.scheduler.Schedulers;

/** Reproduces finding F4 of the review of PR #2113. */
@SpringBootTest
@WireMockTest
class VerifyPr2113F4IT {

  private static final String PID_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";
  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientFactory clientFactory;

  /**
   * F4: patient x has consents on two pages. Page 1 covers A and has B without start, page 2 covers
   * B. Together they cover {A, B}, so x must be consented and not excluded.
   */
  @Test
  void f4ConsentsSpanningTwoPagesAreClassifiedTogether(WireMockRuntimeInfo wireMockRuntime) {
    var config =
        new FhirCohortSelectorConfig(
            new HttpClientConfig("ignored"), PID_SYSTEM, POLICY_SYSTEM, Set.of("A", "B"));
    var selector =
        new FhirCohortSelector(
            config,
            clientFactory.create(MockServerUtil.clientConfig(wireMockRuntime)),
            consentedPatientExtractor(config),
            new DefaultRetryStrategy(meterRegistry),
            Schedulers.parallel(),
            4);

    var page1 =
        Stream.of(patient(), consent("c1", permit("A"), permit("B").setPeriod(null)))
            .collect(toBundle());
    page1.addLink(new Bundle.BundleLinkComponent().setRelation("next").setUrl("/Consent?_page=1"));
    var page2 = Stream.of(patient(), consent("c2", permit("B"))).collect(toBundle());

    var wireMock = wireMockRuntime.getWireMock();
    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_include", equalTo("Consent:patient"))
            .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON))
            .willReturn(fhirResponse(page1)));
    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_page", equalTo("1"))
            .willReturn(fhirResponse(page2)));

    create(selector.selectCohort(List.of()).collectList())
        .assertNext(entries -> assertThat(entries).singleElement().isInstanceOf(Consented.class))
        .verifyComplete();
  }

  private static Patient patient() {
    return (Patient)
        new Patient()
            .addIdentifier(new Identifier().setSystem(PID_SYSTEM).setValue("patient-identifier-x"))
            .setId("patient-x");
  }

  private static Consent.ProvisionComponent permit(String policy) {
    return new Consent.ProvisionComponent()
        .setType(Consent.ConsentProvisionType.PERMIT)
        .setPeriod(new Period().setStart(new Date(0)).setEnd(new Date(4102444800000L)))
        .addCode(
            new CodeableConcept().addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode(policy)));
  }

  private static Consent consent(String id, Consent.ProvisionComponent... provisions) {
    return (Consent)
        new Consent()
            .setStatus(Consent.ConsentState.ACTIVE)
            .setProvision(new Consent.ProvisionComponent().setProvision(List.of(provisions)))
            .setPatient(new Reference("Patient/patient-x"))
            .setId(id);
  }
}
