package care.smith.fts.util;

import static care.smith.fts.util.FhirCodecUtils.isBaseResource;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ca.uhn.fhir.context.FhirContext;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

@Slf4j
public class FhirEncoder extends AbstractEncoder<IBaseResource> {
  private final FhirContext fhir;

  public FhirEncoder(FhirContext fhir) {
    super(APPLICATION_FHIR_JSON, APPLICATION_JSON);
    this.fhir = fhir;
  }

  @Override
  public Flux<DataBuffer> encode(
      Publisher<? extends IBaseResource> in,
      DataBufferFactory bufferFactory,
      ResolvableType type,
      MimeType mimeType,
      Map<String, Object> hints) {
    return Flux.from(in).mapNotNull(r -> encodeValue(r, bufferFactory, type, mimeType, hints));
  }

  @Override
  public DataBuffer encodeValue(
      IBaseResource value,
      DataBufferFactory bufferFactory,
      ResolvableType valueType,
      MimeType mimeType,
      Map<String, Object> hints) {
    DataBuffer dataBuffer = bufferFactory.allocateBuffer(128);

    try (OutputStreamWriter w = new OutputStreamWriter(dataBuffer.asOutputStream())) {
      log.trace("encode {} to {}", valueType, mimeType);
      fhir.newJsonParser().encodeToWriter(value, w);
    } catch (IOException e) {
      log.error("Error encoding value: {}", e.getMessage(), e);
      DataBufferUtils.release(dataBuffer);
      return null;
    }
    return dataBuffer;
  }

  @Override
  public boolean canEncode(ResolvableType elementType, MimeType mimeType) {
    var can =
        elementType.getRawClass() != null
            && isBaseResource(elementType.getRawClass())
            && getEncodableMimeTypes().stream().anyMatch(m -> m.isCompatibleWith(mimeType));
    log.trace("canEncode {} to {}? {}", elementType, mimeType, can);
    return can;
  }
}
