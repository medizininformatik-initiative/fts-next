package care.smith.fts.cda.rest.it.mock;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.Times;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

@Slf4j
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
    rda.when(request).respond(initialSuccessResponse());

    rda.when(request().withMethod("GET").withPath(baseUrl + "/status/processId456"))
        .respond(response().withStatusCode(200));
  }

  public void successWithStatusCode(List<Integer> statusCodes) {
    var rs = new LinkedList<>(statusCodes);

    rda.when(request)
        .respond(
            request ->
                Optional.ofNullable(rs.poll())
                    .map(
                        statusCode -> {
                          log.trace("statusCode: {}", statusCode);
                          return statusCode < 400
                              ? initialSuccessResponse()
                              : response().withStatusCode(statusCode);
                        })
                    .orElseGet(this::initialSuccessResponse));

    rda.when(request().withMethod("GET").withPath(baseUrl + "/status/processId456"))
        .respond(response().withStatusCode(200));
  }

  private HttpResponse initialSuccessResponse() {
    log.trace("initialSuccessResponse with statusCode 200");
    return response()
        .withHeader(
            CONTENT_LOCATION,
            ("http://localhost:%d" + baseUrl + "/status/processId456").formatted(rda.getPort()));
  }

  public void isDown() {
    rda.when(request).error(HttpError.error().withDropConnection(true));
  }

  public void timeout() {
    rda.when(request).respond(request -> null, Delay.minutes(10));
  }

  public void successWithRetryAfter() {
    rda.when(request).respond(initialSuccessResponse());

    var statusRequest = request().withMethod("GET").withPath(baseUrl + "/status/processId456");
    rda.when(statusRequest, Times.exactly(1))
        .respond(response().withStatusCode(202).withHeader(RETRY_AFTER, "2"));
    rda.when(statusRequest, Times.exactly(1))
        .respond(response().withStatusCode(202).withHeader(RETRY_AFTER, "1"));
    rda.when(statusRequest).respond(response().withStatusCode(200));
  }
}
