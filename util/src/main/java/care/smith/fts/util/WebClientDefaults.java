package care.smith.fts.util;

import static java.time.Duration.ofSeconds;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebClientDefaults implements WebClientCustomizer {

  private static final MimeType[] JACKSON_MIME_TYPES =
      new MimeType[] {
        MediaType.APPLICATION_JSON,
        MediaTypes.APPLICATION_FHIR_JSON,
        MediaType.APPLICATION_PROBLEM_JSON
      };

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public WebClientDefaults(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void customize(WebClient.Builder builder) {
    builder
        .clientConnector(new JdkClientHttpConnector(httpClient))
        .filter((r, n) -> n.exchange(r).timeout(ofSeconds(10)))
        .codecs(this::configureObjectMapper);
  }

  private void configureObjectMapper(ClientCodecConfigurer cs) {
    var encoder = new Jackson2JsonEncoder(objectMapper, JACKSON_MIME_TYPES);
    cs.defaultCodecs().jackson2JsonEncoder(encoder);
    var decoder = new Jackson2JsonDecoder(objectMapper, JACKSON_MIME_TYPES);
    cs.defaultCodecs().jackson2JsonDecoder(decoder);
  }
}
