package care.smith.fts.util;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import java.nio.charset.StandardCharsets;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FhirDecoderTest {

  private static final String PATIENT_ID = "Patient/123";

  private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

  private FhirDecoder decoder;

  @BeforeEach
  void setUp() {
    decoder = new FhirDecoder(forR4());
  }

  @Test
  void decodeToMono() {
    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);

    Flux<DataBuffer> input =
        Flux.just(
            bufferFactory.wrap(
                "{\"resourceType\":\"Patient\",\"id\":\"123\"}".getBytes(StandardCharsets.UTF_8)));
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

    DefaultDataBuffer buffer =
        bufferFactory.wrap(
            """
            {"resourceType":"Patient","id":"123"}
            """
                .getBytes(StandardCharsets.UTF_8));
    Flux<DataBuffer> input = Flux.just(buffer);
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

    DefaultDataBuffer buffer =
        bufferFactory.wrap(
            """
                {"resourceType":"Patient","id":"123"}
                """
                .getBytes(StandardCharsets.UTF_8));
    Patient result =
        (Patient) decoder.decode(buffer, resolvableType, MediaType.APPLICATION_JSON, null);
    assertThat(result.getId()).isEqualTo(PATIENT_ID);
  }

  @Test
  void canDecode() {
    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);

    assertThat(decoder.canDecode(resolvableType, MediaType.APPLICATION_JSON)).isTrue();
    assertThat(
            decoder.canDecode(resolvableType, MediaType.valueOf("application/json;charset=UTF-8")))
        .isTrue();
    assertThat(decoder.canDecode(resolvableType, APPLICATION_FHIR_JSON)).isTrue();
    assertThat(
            decoder.canDecode(
                resolvableType, MediaType.valueOf("application/fhir+json;charset=UTF-8")))
        .isTrue();
  }

  @Test
  void cannotDecode() {
    ResolvableType resolvableType = ResolvableType.forClass(String.class);

    assertThat(decoder.canDecode(resolvableType, MediaType.APPLICATION_JSON)).isFalse();
  }
}
