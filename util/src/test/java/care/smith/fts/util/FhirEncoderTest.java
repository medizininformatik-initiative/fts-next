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
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FhirEncoderTest {

  private FhirEncoder encoder;

  @BeforeEach
  void setUp() {
    encoder = new FhirEncoder(forR4());
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

    assertThat(encoder.canEncode(resolvableType, MediaType.APPLICATION_JSON)).isTrue();
    assertThat(
            encoder.canEncode(resolvableType, MediaType.valueOf("application/json;charset=UTF-8")))
        .isTrue();
    assertThat(encoder.canEncode(resolvableType, APPLICATION_FHIR_JSON)).isTrue();
    assertThat(
            encoder.canEncode(
                resolvableType, MediaType.valueOf("application/fhir+json;charset=UTF-8")))
        .isTrue();
  }

  @Test
  void cannotEncode() {
    ResolvableType resolvableType = ResolvableType.forClass(String.class);
    boolean result = encoder.canEncode(resolvableType, MediaType.APPLICATION_JSON);

    assertThat(result).isFalse();
  }
}
