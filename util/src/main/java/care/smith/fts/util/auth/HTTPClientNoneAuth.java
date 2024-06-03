package care.smith.fts.util.auth;

import ca.uhn.fhir.rest.client.api.IRestfulClient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import java.util.HashMap;

@Getter
@Setter
@NoArgsConstructor
public class HTTPClientNoneAuth extends HashMap<Object, Object> implements HTTPClientAuthMethod {

  public static HTTPClientNoneAuth NONE = new HTTPClientNoneAuth();

  @Override
  public void configure(IRestfulClient client) {}

  @Override
  public void configure(HttpClientBuilder client) {}
}
