package care.smith.fts.util;

import static java.time.Duration.ofSeconds;

import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
public class WebClientDefaults implements WebClientCustomizer {

  private static final MimeType[] JACKSON_MIME_TYPES =
      new MimeType[] {
        MediaType.APPLICATION_JSON,
        MediaTypes.APPLICATION_FHIR_JSON,
        MediaType.APPLICATION_PROBLEM_JSON
      };

  private final JsonMapper jsonMapper;

  public WebClientDefaults(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /**
   * Applies shared request defaults. The outbound connector is left to Spring Boot's auto-detected
   * Reactor Netty {@code ClientHttpConnector} (tuned via {@code ftsClientResources}); overriding it
   * here would also discard the connection-pool configuration on the SSL/mTLS path.
   */
  @Override
  public void customize(WebClient.Builder builder) {
    builder
        .filter(
            (request, next) ->
                next.exchange(request)
                    .timeout(ofSeconds(10))
                    .flatMap(WebClientDefaults::errorOnRedirect))
        .codecs(this::configureJacksonCodecs);
  }

  /**
   * A 3xx reaching here means the transport layer did not follow it (a downgrade refused under
   * {@code FOLLOW_SAFE}, or any redirect under {@code DONT_FOLLOW}). Surface it as an error so the
   * transfer fails loudly instead of letting the empty redirect body pass through {@code
   * retrieve()} as a silent success (#1706).
   */
  private static Mono<ClientResponse> errorOnRedirect(ClientResponse response) {
    return response.statusCode().is3xxRedirection()
        ? response.createException().flatMap(Mono::error)
        : Mono.just(response);
  }

  private void configureJacksonCodecs(ClientCodecConfigurer cs) {
    var encoder = new JacksonJsonEncoder(jsonMapper, JACKSON_MIME_TYPES);
    cs.defaultCodecs().jacksonJsonEncoder(encoder);
    var decoder = new JacksonJsonDecoder(jsonMapper, JACKSON_MIME_TYPES);
    cs.defaultCodecs().jacksonJsonDecoder(decoder);
  }
}
