package care.smith.fts.util.auth;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.client.api.IRestfulClient;
import ca.uhn.fhir.rest.client.interceptor.CookieInterceptor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.web.reactive.function.client.WebClient;

@Getter
@Setter
@NoArgsConstructor
public class HTTPClientCookieTokenAuth implements HTTPClientAuthMethod {

  private String token;

  @Override
  public void configure(IRestfulClient client) {
    client.registerInterceptor(new CookieInterceptor(token));
  }

  @Override
  public void configure(HttpClientBuilder client) {
    client.addRequestInterceptorFirst(
        (request, entity, context) -> {
          request.addHeader(Constants.HEADER_COOKIE, token);
        });
  }

  @Override
  public void configure(WebClient.Builder builder) {}
}
