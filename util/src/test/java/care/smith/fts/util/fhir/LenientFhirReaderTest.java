package care.smith.fts.util.fhir;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.fhir.FhirDecoder.LENIENT;
import static care.smith.fts.util.fhir.LenientFhirReader.readLenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.create;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class LenientFhirReaderTest {

  private static final String CONSENT_WITH_MALFORMED_START =
      """
      {"resourceType": "Consent", "provision": {"provision": [{
        "type": "permit", "period": {"start": "2024/02/23", "end": "2054-01-31"}
      }]}}\
      """;

  @Test
  void malformedValueIsKeptAsRawValue() {
    var response = response(APPLICATION_FHIR_JSON, CONSENT_WITH_MALFORMED_START);

    create(read(response, Map.of()))
        .assertNext(
            c ->
                assertThat(
                        c.getProvision()
                            .getProvisionFirstRep()
                            .getPeriod()
                            .getStartElement()
                            .asStringValue())
                    .isEqualTo("2024/02/23"))
        .verifyComplete();
  }

  /** Spring passes e.g. its log prefix as a hint, which must not get lost. */
  @Test
  void hintsOfTheContextArePassedOnWithLenientHint() {
    var passed = new AtomicReference<Map<String, Object>>();
    var recordingReader =
        new DecoderHttpMessageReader<>(new FhirDecoder(forR4())) {
          @Override
          public Mono<IBaseResource> readMono(
              ResolvableType type, ReactiveHttpInputMessage message, Map<String, Object> hints) {
            passed.set(hints);
            return super.readMono(type, message, hints);
          }
        };
    var response = response(APPLICATION_FHIR_JSON, CONSENT_WITH_MALFORMED_START);
    var hints = Map.<String, Object>of(LENIENT, false, "other", "kept");

    create(readLenient(Consent.class).extract(response, context(hints, recordingReader)))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(passed.get()).containsEntry(LENIENT, true).containsEntry("other", "kept");
  }

  @Test
  void serverResponseOfTheContextIsPassedOn() {
    var passed = new AtomicReference<ServerHttpResponse>();
    var recordingReader =
        new DecoderHttpMessageReader<>(new FhirDecoder(forR4())) {
          @Override
          public Mono<IBaseResource> readMono(
              ResolvableType actualType,
              ResolvableType elementType,
              ServerHttpRequest request,
              ServerHttpResponse response,
              Map<String, Object> hints) {
            passed.set(response);
            return super.readMono(actualType, elementType, request, response, hints);
          }
        };
    var request =
        MockServerHttpRequest.post("/")
            .contentType(APPLICATION_FHIR_JSON)
            .body(CONSENT_WITH_MALFORMED_START);
    var serverResponse = new MockServerHttpResponse();
    var context = context(Map.of(), recordingReader, Optional.of(serverResponse));

    create(readLenient(Consent.class).extract(request, context))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(passed.get()).isSameAs(serverResponse);
  }

  @Test
  void otherContentTypeIsRejected() {
    var response = response(MediaType.TEXT_PLAIN, CONSENT_WITH_MALFORMED_START);

    create(read(response, Map.of())).expectError(UnsupportedMediaTypeException.class).verify();
  }

  @Test
  void missingContentTypeWithoutBodyGivesNoResource() {
    create(read(new MockClientHttpResponse(OK), Map.of())).verifyComplete();
  }

  private static Flux<Consent> read(MockClientHttpResponse response, Map<String, Object> hints) {
    var reader = new DecoderHttpMessageReader<>(new FhirDecoder(forR4()));
    return readLenient(Consent.class).extract(response, context(hints, reader));
  }

  private static BodyExtractor.Context context(
      Map<String, Object> hints, HttpMessageReader<?> reader) {
    return context(hints, reader, Optional.empty());
  }

  private static BodyExtractor.Context context(
      Map<String, Object> hints,
      HttpMessageReader<?> reader,
      Optional<ServerHttpResponse> serverResponse) {
    return new BodyExtractor.Context() {
      @Override
      public List<HttpMessageReader<?>> messageReaders() {
        return List.of(reader);
      }

      @Override
      public Optional<ServerHttpResponse> serverResponse() {
        return serverResponse;
      }

      @Override
      public Map<String, Object> hints() {
        return hints;
      }
    };
  }

  private static MockClientHttpResponse response(MediaType contentType, String body) {
    var response = new MockClientHttpResponse(OK);
    response.getHeaders().setContentType(contentType);
    response.setBody(body);
    return response;
  }
}
