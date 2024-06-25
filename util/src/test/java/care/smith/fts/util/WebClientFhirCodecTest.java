package care.smith.fts.util;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.context.FhirContext;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockServerExtension.class)
class WebClientFhirCodecTest {

  private final DataBuffer testDataBuffer =
      new DefaultDataBufferFactory()
          .wrap("{\"resourceType\":\"Patient\",\"id\":\"123\"}".getBytes(StandardCharsets.UTF_8));
  private final String PATIENT_ID = "Patient/123";

  private final WebClientFhirCodec.Encoder encoder =
      new WebClientFhirCodec(FhirUtils.fctx).new Encoder();
  private final WebClientFhirCodec.Decoder decoder =
      new WebClientFhirCodec(FhirUtils.fctx).new Decoder();

  @Test
  void customizeWebClientAndDecodeToMono(MockServerClient mockServer) {
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

  @Test
  void decodeToMono() {
    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);

    Flux<DataBuffer> input = Flux.just(testDataBuffer);
    Mono<Patient> resultMono =
        decoder
            .decodeToMono(input, resolvableType, MediaType.APPLICATION_JSON, null)
            .map(b -> (Patient) b);

    create(resultMono)
        .assertNext(resource -> assertThat(resource.getId()).isEqualTo(PATIENT_ID))
        .verifyComplete();
  }

  @Test
  void decodeFlux() {
    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);

    Flux<DataBuffer> input = Flux.just(testDataBuffer);
    Flux<Patient> resultFlux =
        decoder
            .decode(input, resolvableType, MediaType.APPLICATION_JSON, null)
            .map(b -> (Patient) b);

    create(resultFlux)
        .assertNext(resource -> assertThat(resource.getId()).isEqualTo(PATIENT_ID))
        .verifyComplete();
  }

  @Test
  void decodeDataBuffer() {
    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);

    Patient result =
        (Patient) decoder.decode(testDataBuffer, resolvableType, MediaType.APPLICATION_JSON, null);
    assertThat(result.getId()).isEqualTo(PATIENT_ID);
  }

  @Test
  void canDecode() {
    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);

    assertThat(decoder.canDecode(resolvableType, MediaType.APPLICATION_JSON)).isTrue();
  }

  @Test
  void cannotDecode() {
    ResolvableType resolvableType = ResolvableType.forClass(String.class);

    assertThat(decoder.canDecode(resolvableType, MediaType.APPLICATION_JSON)).isFalse();
  }

  @Test
  void encode() {
    Patient patient = new Patient();
    patient.addName().setFamily("Smith").addGiven("John");

    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);
    DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    Flux<DataBuffer> resultFlux =
        encoder.encode(
            Mono.just(patient), bufferFactory, resolvableType, MediaType.APPLICATION_JSON, null);

    create(resultFlux)
        .assertNext(
            dataBuffer -> {
              String encodedString = encodedString(dataBuffer);
              String expectedString =
                  FhirUtils.fctx.newJsonParser().encodeResourceToString(patient);
              assertThat(encodedString).isEqualTo(expectedString);
            })
        .verifyComplete();
  }

  @Test
  void encodeValue() {
    Patient patient = new Patient();
    patient.addName().setFamily("Smith").addGiven("John");

    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);
    DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    DataBuffer dataBuffer =
        encoder.encodeValue(
            patient, bufferFactory, resolvableType, MediaType.APPLICATION_JSON, null);
    assertThat(dataBuffer).isNotNull();

    String encodedString = encodedString(dataBuffer);
    String expectedString = FhirUtils.fctx.newJsonParser().encodeResourceToString(patient);
    assertThat(encodedString).isEqualTo(expectedString);
  }

  String encodedString(DataBuffer dataBuffer) {
    return DataBufferUtils.join(Mono.just(dataBuffer))
        .map(
            buffer -> {
              byte[] bytes = new byte[buffer.readableByteCount()];
              buffer.read(bytes);
              DataBufferUtils.release(buffer);
              return new String(bytes, StandardCharsets.UTF_8);
            })
        .block();
  }

  @Test
  void canEncode() {
    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);
    boolean result = encoder.canEncode(resolvableType, MediaType.APPLICATION_JSON);

    assertThat(result).isTrue();
  }

  @Test
  void cannotEncode() {
    ResolvableType resolvableType = ResolvableType.forClass(String.class);
    boolean result = encoder.canEncode(resolvableType, MediaType.APPLICATION_JSON);

    assertThat(result).isFalse();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
