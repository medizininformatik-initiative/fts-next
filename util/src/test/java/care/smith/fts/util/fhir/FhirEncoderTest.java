package care.smith.fts.util.fhir;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.JsonParser;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

class FhirEncoderTest {

  private FhirEncoder encoder;

  @BeforeEach
  void setUp() {
    encoder = new FhirEncoder(forR4());
  }

  @Test
  void encode() {
    var patient = new Patient();
    patient.addName().setFamily("Smith").addGiven("John");

    var resolvableType = ResolvableType.forClass(Patient.class);
    var bufferFactory = new DefaultDataBufferFactory();
    var resultFlux =
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
    var patient = new Patient();
    patient.addName().setFamily("Smith").addGiven("John");

    var resolvableType = ResolvableType.forClass(Patient.class);
    var bufferFactory = new DefaultDataBufferFactory();

    var dataBuffer =
        encoder.encodeValue(
            patient, bufferFactory, resolvableType, MediaType.APPLICATION_JSON, null);
    assertThat(dataBuffer).isNotNull();

    var encodedString = encodedString(dataBuffer);
    var expectedString = FhirUtils.fctx.newJsonParser().encodeResourceToString(patient);
    assertThat(encodedString).isEqualTo(expectedString);
  }

  @Test
  public void encodeValueIOException() throws IOException {
    var value = mock(IBaseResource.class);
    var context = mock(FhirContext.class);
    var jsonParser = mock(JsonParser.class);
    var bufferFactory = new DefaultDataBufferFactory();

    when(context.newJsonParser()).thenReturn(jsonParser);
    doThrow(new IOException("Test IOException"))
        .when(jsonParser)
        .encodeToWriter(any(IBaseResource.class), any(OutputStreamWriter.class));

    var encoder = new FhirEncoder(context);
    var encodedValue =
        encoder.encodeValue(
            value,
            bufferFactory,
            ResolvableType.forClass(IBaseResource.class),
            MediaType.APPLICATION_JSON,
            null);
    assertThat(encodedValue).isNull();
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
    var resolvableType = ResolvableType.forClass(Patient.class);

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
  void cannotEncodeIncompatibleResolvableType() {
    var resolvableType = ResolvableType.forClass(String.class);
    var canEncode = encoder.canEncode(resolvableType, MediaType.APPLICATION_JSON);
    assertThat(canEncode).isFalse();
  }

  @Test
  void cannotEncodeIncompatibleMimeType() {
    var resolvableType = ResolvableType.forClass(Patient.class);
    var canEncode = encoder.canEncode(resolvableType, MediaType.APPLICATION_PDF);

    assertThat(canEncode).isFalse();
  }
}
