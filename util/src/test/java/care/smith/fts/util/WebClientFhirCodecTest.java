package care.smith.fts.util;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockServerExtension.class)
class WebClientFhirCodecTest {

  @Test
  void decodeToMono(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());

    Bundle bundle = new Bundle();
    mockServer
        .when(request().withMethod("POST").withPath("/"))
        .respond(response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON));

    WebClient.Builder webClientBuilder = WebClient.builder();
    new WebClientFhirCodec(FhirContext.forR4()).customize(webClientBuilder);
    WebClient webClient = webClientBuilder.baseUrl(address).build();

    create(webClient.post().retrieve().bodyToMono(Bundle.class))
        .assertNext(b -> b.equalsDeep(bundle))
        .verifyComplete();
  }
}
