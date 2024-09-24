package care.smith.fts.tca.rest;

import static care.smith.fts.util.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.consent.ConsentedPatientsProvider;
import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.ConsentRequest;
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
  ConsentRequest consentRequest = new ConsentRequest("MII", Set.of(), "sys", List.of("id1"));

  @BeforeEach
  void setUp() {
    this.controller = new ConsentController(provider, 1);
  }

  List<Mono<ResponseEntity<Bundle>>> responses() {
    return List.of(
        controller.fetchAll(
            Mono.just(consentRequest), requestUrl, Optional.empty(), Optional.empty()),
        controller.fetch(
            Mono.just(consentRequest), requestUrl, Optional.empty(), Optional.empty()));
  }

  @Test
  void fetchAllEmptyPageYieldsEmptyBundle() {
    var bundle = Stream.<Resource>empty().collect(toBundle());
    given(provider.fetchAll(consentRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.just(bundle));
    given(provider.fetch(consentRequest, requestUrl, new PagingParams(0, 1)))
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
    given(provider.fetchAll(consentRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.error(new UnknownDomainException("")));
    given(provider.fetch(consentRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.error(new UnknownDomainException("")));
    responses()
        .forEach(
            response ->
                create(response)
                    .assertNext(
                        b -> assertThat(b.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                    .verifyComplete());
  }

  @Test
  void fetchErrorResponseYieldsInternalServerError() {
    given(provider.fetchAll(consentRequest, requestUrl, new PagingParams(0, 1)))
        .willReturn(Mono.error(new IllegalArgumentException()));
    given(provider.fetch(consentRequest, requestUrl, new PagingParams(0, 1)))
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
