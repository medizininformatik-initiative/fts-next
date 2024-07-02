package care.smith.fts.tca.rest;

import static care.smith.fts.util.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.consent.ConsentProvider;
import care.smith.fts.util.tca.ConsentRequest;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ConsentControllerTest {

  @Mock ConsentProvider provider;

  private ConsentController controller;

  @BeforeEach
  void setUp() {
    this.controller = new ConsentController(provider, 1);
  }

  @Test
  void emptyPageYieldsEmptyBundle() {
    var bundle = Stream.<Resource>empty().collect(toBundle());
    var request = new ConsentRequest("MII", Set.of(), "sys");
    var requestUrl = fromUriString("/fake/");
    given(provider.consentedPatientsPage("MII", "sys", Set.of(), requestUrl, 0, 1))
        .willReturn(Mono.just(bundle));

    var response =
        controller.consentedPatients(
            Mono.just(request), requestUrl, Optional.empty(), Optional.empty());

    create(response).assertNext(b -> assertThat(b.getBody()).isEqualTo(bundle)).verifyComplete();
  }
}
