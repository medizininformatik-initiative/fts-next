package care.smith.fts.cda.rest.it.mock;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;

public class MockBundleSender {

  private final MockServerClient rda;

  public MockBundleSender(MockServerClient rda) {
    this.rda = rda;
  }

  public void success() {
    rda.when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/test/patient")
                .withBody(
                    json(
                        "{\"resourceType\":\"Bundle\",\"total\":2}",
                        MatchType.ONLY_MATCHING_FIELDS)))
        .respond(response());
  }

  public void isDown() {
    rda.when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/test/patient")
                .withBody(
                    json(
                        "{\"resourceType\":\"Bundle\",\"total\":2}",
                        MatchType.ONLY_MATCHING_FIELDS)))
        .error(HttpError.error().withDropConnection(true));
  }

  public void timeout() {
    rda.when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/test/patient")
                .withBody(
                    json(
                        "{\"resourceType\":\"Bundle\",\"total\":2}",
                        MatchType.ONLY_MATCHING_FIELDS)))
        .respond(request -> null, Delay.minutes(10));
  }
}
