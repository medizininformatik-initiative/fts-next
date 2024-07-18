package care.smith.fts.cda.rest.it.mock;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.Times;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

public class MockBundleSender {

  private final MockServerClient rda;
  private final HttpRequest request;
  private final String baseUrl;

  public MockBundleSender(MockServerClient rda) {
    this.rda = rda;
    baseUrl = "/api/v2/process";
    request =
        request()
            .withMethod("POST")
            .withPath(baseUrl + "/test/patient")
            .withBody(
                json("{\"resourceType\":\"Bundle\",\"total\":2}", MatchType.ONLY_MATCHING_FIELDS));
  }

  public void success() {
    rda.when(request)
        .respond(
            response()
                .withHeader(
                    CONTENT_LOCATION,
                    ("http://localhost:%d" + baseUrl + "/status/processId456")
                        .formatted(rda.getPort())));

    rda.when(request().withMethod("GET").withPath(baseUrl + "/status/processId456"))
        .respond(response().withStatusCode(200));
  }

  public void isDown() {
    rda.when(request).error(HttpError.error().withDropConnection(true));
  }

  public void timeout() {
    rda.when(request).respond(request -> null, Delay.minutes(10));
  }

  public void successWithRetryAfter() {
    rda.when(request)
        .respond(
            response()
                .withHeader(
                    CONTENT_LOCATION,
                    ("http://localhost:%d" + baseUrl + "/status/processId456")
                        .formatted(rda.getPort())));

    var statusRequest = request().withMethod("GET").withPath(baseUrl + "/status/processId456");
    rda.when(statusRequest, Times.exactly(1))
        .respond(response().withStatusCode(202).withHeader(RETRY_AFTER, "2"));
    rda.when(statusRequest, Times.exactly(1))
        .respond(response().withStatusCode(202).withHeader(RETRY_AFTER, "1"));
    rda.when(statusRequest).respond(response().withStatusCode(200));
  }
}
