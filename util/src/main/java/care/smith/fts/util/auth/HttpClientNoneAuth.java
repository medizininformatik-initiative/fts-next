package care.smith.fts.util.auth;

import java.util.HashMap;
import org.springframework.web.reactive.function.client.WebClient;

public class HttpClientNoneAuth extends HashMap<Object, Object> implements HttpClientAuthMethod {

  public static HttpClientNoneAuth NONE = new HttpClientNoneAuth();

  @Override
  public void configure(WebClient.Builder builder) {}
}
