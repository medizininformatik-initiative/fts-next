package care.smith.fts.util.fhir;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.fhir.FhirDecoder.LENIENT;
import static care.smith.fts.util.fhir.FhirUtils.fhirResourceToString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.parser.DataFormatException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FhirDecoderTest {

  private static final String PATIENT_ID = "Patient/123";
  private static final ResolvableType CONSENT = ResolvableType.forClass(Consent.class);
  private static final String CONSENT_WITH_MALFORMED_START =
      """
      {"resourceType": "Consent", "provision": {"provision": [{
        "type": "permit", "period": {"start": "2024/02/23", "end": "2054-01-31"}
      }]}}\
      """;

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
  void malformedValueFailsByDefault() {
    var buffer = bufferFactory.wrap(CONSENT_WITH_MALFORMED_START.getBytes(UTF_8));

    assertThatThrownBy(() -> decoder.decode(buffer, CONSENT, APPLICATION_FHIR_JSON, null))
        .isInstanceOf(DataFormatException.class);
  }

  @Test
  void malformedValueFailsWithoutLenientHint() {
    var buffer = bufferFactory.wrap(CONSENT_WITH_MALFORMED_START.getBytes(UTF_8));
    var hints = Map.<String, Object>of(LENIENT, false);

    assertThatThrownBy(() -> decoder.decode(buffer, CONSENT, APPLICATION_FHIR_JSON, hints))
        .isInstanceOf(DataFormatException.class);
  }

  @Test
  void malformedValueIsKeptAsRawValueWithLenientHint() {
    var consent = decodeLenient(CONSENT_WITH_MALFORMED_START);

    assertThat(consent.getProvision().getProvisionFirstRep().getPeriod().getStartElement())
        .satisfies(start -> assertThat(start.hasValue()).isTrue())
        .satisfies(start -> assertThat(start.asStringValue()).isEqualTo("2024/02/23"));
  }

  @Test
  void malformedValueIsNotLoggedWithLenientHint() {
    var logger = (Logger) LoggerFactory.getLogger("ca.uhn.fhir");
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      decodeLenient(CONSENT_WITH_MALFORMED_START);

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .noneMatch(m -> m.contains("2024/02/23"));
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  void malformedValueIsEncodedAsReadWithLenientHint() {
    var consent = decodeLenient(CONSENT_WITH_MALFORMED_START);

    assertThat(fhirResourceToString(consent)).contains("\"start\":\"2024/02/23\"");
  }

  @Test
  void bodyThatIsNoFhirJsonFailsWithLenientHint() {
    assertThatThrownBy(() -> decodeLenient("invalid")).isInstanceOf(DataFormatException.class);
  }

  private Consent decodeLenient(String body) {
    var buffer = bufferFactory.wrap(body.getBytes(UTF_8));
    return (Consent) decoder.decode(buffer, CONSENT, APPLICATION_FHIR_JSON, Map.of(LENIENT, true));
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

  @Test
  void cannotDecodeIncompatibleMimeType() {
    ResolvableType resolvableType = ResolvableType.forClass(Patient.class);

    assertThat(decoder.canDecode(resolvableType, MediaType.APPLICATION_PDF)).isFalse();
  }
}
