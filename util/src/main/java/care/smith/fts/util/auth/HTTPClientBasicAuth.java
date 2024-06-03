package care.smith.fts.util.auth;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.client.api.IRestfulClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

@Getter
@Setter
@NoArgsConstructor
public class HTTPClientBasicAuth implements HTTPClientAuthMethod {

  private String user;
  private String password;

  @Override
  public void configure(IRestfulClient client) {
    client.registerInterceptor(new BasicAuthInterceptor(this.user, this.password));
  }

  @Override
  public void configure(HttpClientBuilder client) {
    String authString = "%s:%s".formatted(this.user, this.password);
    String encoded = encodeBase64String(authString.getBytes(Constants.CHARSET_US_ASCII));
    String header = "Basic " + encoded;
    client.addRequestInterceptorFirst(
        (request, entity, context) -> {
          request.addHeader(Constants.HEADER_AUTHORIZATION, header);
        });
  }
}
