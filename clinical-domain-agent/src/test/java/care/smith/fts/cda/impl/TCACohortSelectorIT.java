package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ProblemDetail;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootTest
@WireMockTest
class TCACohortSelectorIT extends AbstractConnectionScenarioIT {

  @Autowired MeterRegistry meterRegistry;
  private WireMock wireMock;

  private static final Set<String> POLICIES = Set.of("any");

  private static final String PID_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";
  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";

  private static TCACohortSelector cohortSelector;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var address = "http://localhost";
    var server = new HttpClientConfig(address);
    var config = new TCACohortSelectorConfig(server, PID_SYSTEM, POLICY_SYSTEM, POLICIES, "MII");
    cohortSelector =
        new TCACohortSelector(
            config, clientFactory.create(clientConfig(wireMockRuntime)), meterRegistry);
    wireMock = wireMockRuntime.getWireMock();
  }

  private static MappingBuilder getBuilderFetchAll() {
    return post("/api/v2/cd/consented-patients/fetch-all")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
  }

  private static MappingBuilder getBuilderFetch() {
    return post("/api/v2/cd/consented-patients/fetch")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
  }

  @Override
  protected Stream<TestStep<?>> createTestSteps() {
    return Stream.of(
        new TestStep<ConsentedPatient>() {
          @Override
          public MappingBuilder getBuilder() {
            return TCACohortSelectorIT.getBuilderFetchAll();
          }

          @Override
          public Flux<ConsentedPatient> executeStep() {
            return TCACohortSelectorIT.cohortSelector.selectCohort(List.of());
          }
        },
        new TestStep<ConsentedPatient>() {
          @Override
          public MappingBuilder getBuilder() {
            return TCACohortSelectorIT.getBuilderFetch();
          }

          @Override
          public Flux<ConsentedPatient> executeStep() {
            return TCACohortSelectorIT.cohortSelector.selectCohort(List.of("id"));
          }
        });
  }

  @Test
  void responseInvalidErrors() {
    wireMock.register(getBuilderFetchAll().willReturn(ok().withBody("invalid")));
    create(cohortSelector.selectCohort(List.of())).expectError().verify();
  }

  @Test
  void badRequestErrors() throws JsonProcessingException {
    var om = new ObjectMapper();
    wireMock.register(
        getBuilderFetchAll()
            .willReturn(
                badRequest()
                    .withBody(
                        om.writeValueAsString(
                            ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Some TCA Error")))));

    create(cohortSelector.selectCohort(List.of())).expectError().verify();
  }

  @Test
  void consentBundleSucceeds() {
    Bundle inner =
        Stream.of(
                new Patient()
                    .addIdentifier(
                        new Identifier().setSystem(PID_SYSTEM).setValue("patient-122651")),
                new Consent().setProvision(denyProvision()))
            .collect(toBundle());
    Bundle outer = Stream.of(inner).collect(toBundle());

    wireMock.register(
        getBuilderFetchAll()
            .willReturn(
                ok().withHeader("Content-Type", "application/fhir+json")
                    .withBody(fhirResourceToString(outer))));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void consentBundleForIdsSucceeds() {
    Bundle inner =
        Stream.of(
                new Patient()
                    .addIdentifier(
                        new Identifier().setSystem(PID_SYSTEM).setValue("patient-122651")),
                new Consent().setProvision(denyProvision()))
            .collect(toBundle());
    Bundle outer = Stream.of(inner).collect(toBundle());

    wireMock.register(
        getBuilderFetch()
            .willReturn(
                ok().withHeader("Content-Type", "application/fhir+json")
                    .withBody(fhirResourceToString(outer))));

    create(cohortSelector.selectCohort(List.of("patient-122651")))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void emptyOuterBundleGivesEmptyResult() {
    Bundle outer = Stream.<Resource>of().collect(toBundle());

    wireMock.register(
        getBuilderFetchAll()
            .willReturn(
                ok().withHeader("Content-Type", "application/fhir+json")
                    .withBody(fhirResourceToString(outer))));
    create(cohortSelector.selectCohort(List.of())).verifyComplete();
  }

  @Test
  void emptyInnerBundleGivesEmptyResult() {
    Bundle outer = Stream.of(Stream.<Resource>of().collect(toBundle())).collect(toBundle());
    wireMock.register(
        getBuilderFetchAll()
            .willReturn(
                ok().withHeader("Content-Type", "application/fhir+json")
                    .withBody(fhirResourceToString(outer))));
    create(cohortSelector.selectCohort(List.of())).verifyComplete();
  }

  private static Consent.ProvisionComponent denyProvision() {
    return new Consent.ProvisionComponent()
        .setType(Consent.ConsentProvisionType.DENY)
        .addProvision(permitProvision());
  }

  private static Consent.ProvisionComponent permitProvision() {
    var policy =
        new CodeableConcept().addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("any"));
    return new Consent.ProvisionComponent()
        .setType(Consent.ConsentProvisionType.PERMIT)
        .setCode(List.of(policy))
        .setPeriod(new Period().setStart(new Date(1)).setEnd(new Date(2)));
  }
}
