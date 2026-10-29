package care.smith.fts.util.fhir;

import static care.smith.fts.util.fhir.FhirDecoder.LENIENT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyExtractors;
import reactor.core.publisher.Flux;

/** Reads FHIR responses with the {@link FhirDecoder#LENIENT} hint set. */
public interface LenientFhirReader {

  /**
   * Reads a resource like {@code bodyToMono} does, keeping malformed primitive values as raw text.
   * Use it with {@code retrieve().toEntityFlux(...)}, so that WebClient handles its errors like
   * those of {@code bodyToMono}.
   *
   * @param type the resource type to read
   * @return an extractor emitting the resource, or nothing if the body is empty
   */
  static <T extends IBaseResource> BodyExtractor<Flux<T>, ReactiveHttpInputMessage> readLenient(
      Class<T> type) {
    return (message, context) ->
        BodyExtractors.toMono(type).extract(message, withLenientHint(context)).flux();
  }

  private static BodyExtractor.Context withLenientHint(BodyExtractor.Context context) {
    var hints = new HashMap<>(context.hints());
    hints.put(LENIENT, true);
    return new BodyExtractor.Context() {
      @Override
      public List<HttpMessageReader<?>> messageReaders() {
        return context.messageReaders();
      }

      @Override
      public Optional<ServerHttpResponse> serverResponse() {
        return context.serverResponse();
      }

      @Override
      public Map<String, Object> hints() {
        return hints;
      }
    };
  }
}
