package care.smith.fts.util.auth;

import ca.uhn.fhir.rest.client.api.IRestfulClient;
import lombok.*;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.web.reactive.function.client.WebClient;

public interface HTTPClientAuthMethod {
  void configure(IRestfulClient client);

  void configure(HttpClientBuilder client);

  void configure(WebClient.Builder builder);

  @Builder
  record AuthMethod(
      HTTPClientBasicAuth basic, HTTPClientCookieTokenAuth cookieToken, HTTPClientNoneAuth none) {

    public static AuthMethod NONE = AuthMethod.builder().none(HTTPClientNoneAuth.NONE).build();
  }
}
