package care.smith.fts.cda.impl;

import static org.mockito.BDDMockito.given;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.web.reactive.function.client.WebClient.builder;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.Period;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.PatientIdResolver;
import care.smith.fts.util.HTTPClientConfig;
import java.io.InputStream;
import java.time.ZonedDateTime;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class EverythingDataSelectorTest {

  private static final String PATIENT_ID = "patient-112348";
  private static final PatientIdResolver patient = pid -> Mono.just(new IdType("Patient", pid));

  @Mock ClientResponse response;
  private final DataSelector.Config common = new DataSelector.Config(false, null);
  private final HTTPClientConfig server = new HTTPClientConfig("http://localhost");

  @Test
  void noConsentErrors() {
    var client = builder();
    var dataSelector = new EverythingDataSelector(common, server.createClient(client), patient);

    create(dataSelector.select(new ConsentedPatient(PATIENT_ID))).expectError().verify();
  }

  @Test
  void noConsentSucceedsIfConsentIgnored() {
    var client = builder().exchangeFunction(req -> Mono.just(ClientResponse.create(OK).build()));
    DataSelector.Config common = new DataSelector.Config(true, null);
    var dataSelector = new EverythingDataSelector(common, server.createClient(client), patient);

    create(dataSelector.select(new ConsentedPatient(PATIENT_ID))).verifyComplete();
  }

  @Test
  void selectionSucceeds() throws Exception {

    var client = builder().exchangeFunction(req -> just(response));
    given(response.statusCode()).willReturn(OK);
    try (InputStream inStream = getClass().getResourceAsStream("patient.json")) {
      var bundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, inStream);
      given(response.bodyToMono(Bundle.class)).willReturn(Mono.just(bundle));
    }
    var dataSelector = new EverythingDataSelector(common, server.createClient(client), patient);

    var consentedPolicies = new ConsentedPolicies();
    consentedPolicies.put("pol", new Period(ZonedDateTime.now(), ZonedDateTime.now().plusYears(5)));
    create(dataSelector.select(new ConsentedPatient(PATIENT_ID, consentedPolicies)))
        .expectNextCount(1)
        .verifyComplete();
  }
}
