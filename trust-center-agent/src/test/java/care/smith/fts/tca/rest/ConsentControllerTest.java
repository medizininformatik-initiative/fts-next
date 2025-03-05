package care.smith.fts.tca.rest;

import static care.smith.fts.util.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.consent.ConsentedPatientsProvider;
import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.util.error.fhir.FhirUnknownDomainException;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import care.smith.fts.util.tca.ConsentFetchRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ConsentControllerTest {

  @Mock ConsentedPatientsProvider provider;

  private ConsentController controller;
  UriComponentsBuilder requestUrl = fromUriString("/fake/");
  ConsentFetchAllRequest consentFetchAllRequest =
      new ConsentFetchAllRequest("MII", Set.of(), "sys");
  ConsentFetchRequest consentFetchRequest =
      new ConsentFetchRequest("MII", Set.of(), "ps", "pis", List.of("id1"));

  @BeforeEach
  void setUp() {
    this.controller = new ConsentController(provider, 1);
  }

  List<Mono<ResponseEntity<Bundle>>> responses() {
    return List.of(
        controller.fetchAll(
            Mono.just(consentFetchAllRequest), requestUrl, Optional.empty(), Optional.empty()),
        controller.fetch(
            Mono.just(consentFetchRequest), requestUrl, Optional.empty(), Optional.empty()));
  }

  @Test
  void fetchAllEmptyPageYieldsEmptyBundle() {
    var bundle = Stream.<Resource>empty().collect(toBundle());
    given(provider.fetchAll(consentFetchAllRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.just(bundle));
    given(provider.fetch(consentFetchRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.just(bundle));
    responses()
        .forEach(
            response ->
                create(response)
                    .assertNext(b -> assertThat(b.getBody()).isEqualTo(bundle))
                    .verifyComplete());
  }

  @Test
  void fetchErrorResponseYieldsBadRequest() {
    given(provider.fetchAll(consentFetchAllRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.error(new FhirUnknownDomainException("")));
    given(provider.fetch(consentFetchRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.error(new FhirUnknownDomainException("")));
    responses()
        .forEach(
            response ->
                create(response)
                    .assertNext(b -> assertThat(b.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                    .verifyComplete());
  }

  @Test
  void fetchErrorResponseYieldsInternalServerError() {
    given(provider.fetchAll(consentFetchAllRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.error(new IllegalArgumentException()));
    given(provider.fetch(consentFetchRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.error(new IllegalArgumentException()));
    responses()
        .forEach(
            response ->
                create(response)
                    .assertNext(
                        b ->
                            assertThat(b.getStatusCode())
                                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                    .verifyComplete());
  }
}
