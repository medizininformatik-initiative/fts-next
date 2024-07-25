package care.smith.fts.rda.rest.it.mock;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;

public class MockBundleSender {
  private final MockServerClient hds;
  private final HttpRequest request;

  public MockBundleSender(MockServerClient hds) {
    this.hds = hds;
    request =
        request()
            .withMethod("POST")
            .withContentType(MediaType.parse(APPLICATION_FHIR_JSON_VALUE))
            .withBody(json("{\"resourceType\":\"Bundle\"}", MatchType.ONLY_MATCHING_FIELDS));
  }

  public void success() {
    success(List.of());
  }

  public void success(List<Integer> statusCodes) {
    var rs = new LinkedList<>(statusCodes);
    hds.when(request)
        .respond(
            request ->
                Optional.ofNullable(rs.poll())
                    .map(statusCode -> response().withStatusCode(statusCode))
                    .orElseGet(() -> response().withStatusCode(200)));
  }

  public void isDown() {
    hds.when(request()).error(HttpError.error().withDropConnection(true));
  }

  public void hasTimeout() {
    hds.when(request()).respond(request -> null, Delay.minutes(10));
  }
}
