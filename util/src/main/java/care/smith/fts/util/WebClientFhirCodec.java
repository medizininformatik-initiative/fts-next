package care.smith.fts.util;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.springframework.core.io.buffer.DataBufferUtils.join;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ca.uhn.fhir.context.FhirContext;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.reactivestreams.Publisher;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class WebClientFhirCodec implements WebClientCustomizer {

  private final FhirContext fhir;

  public WebClientFhirCodec(FhirContext fhir) {
    this.fhir = fhir;
  }

  @Override
  public void customize(WebClient.Builder builder) {
    builder.codecs(conf -> conf.customCodecs().register(new Decoder()));
    builder.codecs(conf -> conf.customCodecs().register(new Encoder()));
    log.debug("WebClientFhirCodec registered");
  }

  class Decoder extends AbstractDecoder<IBaseResource> {
    public Decoder() {
      super(APPLICATION_FHIR_JSON, APPLICATION_JSON);
    }

    @Override
    public Mono<IBaseResource> decodeToMono(
        Publisher<DataBuffer> in,
        ResolvableType type,
        MimeType mimeType,
        Map<String, Object> hints) {
      log.trace("Decode to Mono");
      return join(in).mapNotNull(b -> decode(b, type, mimeType, hints));
    }

    @Override
    public Flux<IBaseResource> decode(
        Publisher<DataBuffer> in,
        ResolvableType type,
        MimeType mimeType,
        Map<String, Object> hints) {
      log.trace("Decode to Flux");
      return Flux.from(in).mapNotNull(b -> decode(b, type, mimeType, hints));
    }

    @Override
    public IBaseResource decode(
        DataBuffer buffer, ResolvableType targetType, MimeType mimeType, Map<String, Object> hints)
        throws DecodingException {
      log.trace("decode {} from {}", targetType, mimeType);
      return fhir.newJsonParser()
          .parseResource(ensureBaseResource(targetType), buffer.asInputStream());
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

  private static Class<? extends IBaseResource> ensureBaseResource(ResolvableType type) {
    if (isBaseResource(type.getRawClass())) {
      return (Class<? extends IBaseResource>) type.getRawClass();
    } else {
      throw new IllegalArgumentException("Unsupported resource type: " + type.getRawClass());
    }
  }

  private static boolean isBaseResource(Class<?> clazz) {
    return clazz != null && IBaseResource.class.isAssignableFrom(clazz);
  }

  class Encoder extends AbstractEncoder<IBaseResource> {
    public Encoder() {
      super(APPLICATION_FHIR_JSON, APPLICATION_JSON);
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
      OutputStreamWriter w = new OutputStreamWriter(dataBuffer.asOutputStream());
      try {
        log.trace("encode {} to {}", valueType, mimeType);
        fhir.newJsonParser().encodeToWriter(value, w);
      } catch (IOException e) {
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
}
