package care.smith.fts.util.auth;

import lombok.*;
import org.springframework.web.reactive.function.client.WebClient;

public interface HTTPClientAuthMethod {
  void configure(WebClient.Builder builder);

  @Builder
  record AuthMethod(
      HTTPClientBasicAuth basic, HTTPClientCookieTokenAuth cookieToken, HTTPClientNoneAuth none) {

    public static AuthMethod NONE = AuthMethod.builder().none(HTTPClientNoneAuth.NONE).build();
  }
}
