package care.smith.fts.rda.rest.it.mock;

import static care.smith.fts.test.TidPidMap.getTidPidMapAsJson;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import care.smith.fts.util.tca.ResolveResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

public class MockDeidentifier {

  private final ObjectMapper om;
  private final MockServerClient tca;

  public MockDeidentifier(ObjectMapper om, MockServerClient tca) {
    this.om = om;
    this.tca = tca;
  }

  public void success() {
    success(List.of());
  }

  public void success(List<Integer> statusCodes) {
    var rs = new LinkedList<>(statusCodes);
    tca.when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/rd/resolve-pseudonyms")
                .withContentType(APPLICATION_JSON))
        .respond(
            request -> {
              var tidPidMap = om.readValue(getTidPidMapAsJson(), Map.class);
              var resolveResponse = new ResolveResponse(tidPidMap, Duration.ofMillis(12345));
              var body = om.writeValueAsString(resolveResponse);
              return Optional.ofNullable(rs.poll())
                  .map(
                      statusCode ->
                          statusCode < 400
                              ? successResponse(statusCode, body)
                              : response().withStatusCode(statusCode))
                  .orElseGet(() -> successResponse(200, body));
            });
  }

  private HttpResponse successResponse(int statusCode, String body) {
    return response().withStatusCode(statusCode).withContentType(APPLICATION_JSON).withBody(body);
  }

  public void isDown() {
    tca.when(request()).error(HttpError.error().withDropConnection(true));
  }

  public void hasTimeout() {
    tca.when(request()).respond(request -> null, Delay.minutes(10));
  }

  public void returnsWrongContentType() {
    tca.when(request().withMethod("POST").withPath("/api/v2/rd/resolve-pseudonyms"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withContentType(MediaType.PLAIN_TEXT_UTF_8));
  }
}
