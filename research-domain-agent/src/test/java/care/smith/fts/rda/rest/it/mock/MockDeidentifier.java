package care.smith.fts.rda.rest.it.mock;

import static java.util.stream.Collectors.toMap;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Function;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;

public class MockDeidentifier {

  private final ObjectMapper om;
  private final MockServerClient tca;
  private final HttpRequest request;

  public MockDeidentifier(ObjectMapper om, MockServerClient tca) {
    this.om = om;
    this.tca = tca;
    request =
        request()
            .withMethod("POST")
            .withPath("/api/v2/rd/resolve-pseudonyms")
            .withContentType(APPLICATION_JSON);
  }

  public void success() {
    tca.when(request)
        .respond(
            request -> {
              String body = request.getBodyAsString();

              @SuppressWarnings("unchecked")
              List<String> tid = om.readValue(body, List.class);
              var sidMap = tid.stream().collect(toMap(Function.identity(), Function.identity()));

              return response()
                  .withStatusCode(200)
                  .withContentType(APPLICATION_JSON)
                  .withBody(om.writeValueAsString(sidMap));
            });
  }
}
