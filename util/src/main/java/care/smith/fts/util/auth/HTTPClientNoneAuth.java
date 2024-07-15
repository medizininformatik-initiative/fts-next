package care.smith.fts.util.auth;

import java.util.HashMap;
import org.springframework.web.reactive.function.client.WebClient;

public class HTTPClientNoneAuth extends HashMap<Object, Object> implements HTTPClientAuthMethod {

  public static HTTPClientNoneAuth NONE = new HTTPClientNoneAuth();

  @Override
  public void configure(WebClient.Builder builder) {}
}
