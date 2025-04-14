package care.smith.fts.util.fhir;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.fhir.FhirCodecUtils.ensureBaseResource;
import static care.smith.fts.util.fhir.FhirCodecUtils.isBaseResource;
import static org.springframework.core.io.buffer.DataBufferUtils.join;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ca.uhn.fhir.context.FhirContext;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FhirDecoder extends AbstractDecoder<IBaseResource> {
  private final FhirContext fhir;

  public FhirDecoder(FhirContext fhir) {
    super(APPLICATION_FHIR_JSON, APPLICATION_JSON);
    this.fhir = fhir;
  }

  @Override
  public Mono<IBaseResource> decodeToMono(
      Publisher<DataBuffer> in, ResolvableType type, MimeType mimeType, Map<String, Object> hints) {
    log.trace("Decode to Mono");
    return join(in).mapNotNull(b -> decode(b, type, mimeType, hints));
  }

  @Override
  public Flux<IBaseResource> decode(
      Publisher<DataBuffer> in, ResolvableType type, MimeType mimeType, Map<String, Object> hints) {
    log.trace("Decode to Flux");
    return Flux.from(in).mapNotNull(b -> decode(b, type, mimeType, hints));
  }

  @Override
  public IBaseResource decode(
      DataBuffer buffer, ResolvableType targetType, MimeType mimeType, Map<String, Object> hints)
      throws DecodingException {
    log.trace("decode {} from {}", targetType, mimeType);
    try {
      return fhir.newJsonParser()
          .parseResource(ensureBaseResource(targetType), buffer.asInputStream());
    } finally {
      DataBufferUtils.release(buffer);
    }
  }

  @Override
  public boolean canDecode(ResolvableType elementType, MimeType mimeType) {
    boolean can =
        isBaseResource(elementType.getRawClass())
            && getDecodableMimeTypes().stream().anyMatch(m -> m.isCompatibleWith(mimeType));
    log.trace("canDecode {} from {}? {}", elementType, mimeType, can);
    return can;
  }
}
