package care.smith.fts.util.auth;

import ca.uhn.fhir.rest.client.api.IRestfulClient;
import lombok.*;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

public interface HTTPClientAuthMethod {
  void configure(IRestfulClient client);

  void configure(HttpClientBuilder client);

  @Builder
  record AuthMethod(
      HTTPClientBasicAuth basic, HTTPClientCookieTokenAuth cookieToken, HTTPClientNoneAuth none) {

    public static AuthMethod NONE = AuthMethod.builder().none(HTTPClientNoneAuth.NONE).build();
  }
}
