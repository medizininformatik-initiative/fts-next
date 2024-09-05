package care.smith.fts.util.auth;

import org.springframework.web.reactive.function.client.WebClient;

public interface HttpClientAuthMethod {
  void configure(WebClient.Builder builder);

  record AuthMethod(
      HttpClientBasicAuth basic, HttpClientCookieTokenAuth cookieToken, HttpClientNoneAuth none) {

    public static AuthMethod NONE = new AuthMethod(null, null, HttpClientNoneAuth.NONE);
  }
}
