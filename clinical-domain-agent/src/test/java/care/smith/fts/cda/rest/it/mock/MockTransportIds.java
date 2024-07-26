package care.smith.fts.cda.rest.it.mock;

import static java.util.stream.Collectors.toMap;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import care.smith.fts.util.tca.PseudonymizeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@Slf4j
@Builder
public class MockTransportIds {

  private final ObjectMapper om;
  private final MockServerClient tca;
  private final HttpRequest mockRequestSpec;
  private final Set<String> transportIds;

  public MockTransportIds(
      ObjectMapper om,
      MockServerClient tca,
      HttpRequest mockRequestSpec,
      Set<String> transportIds) {
    this.om = om;
    this.tca = tca;
    this.mockRequestSpec = mockRequestSpec;
    this.transportIds = transportIds;
  }

  public void success() throws JsonProcessingException {
    var tidMap = transportIds.stream().collect(toMap(Function.identity(), Function.identity()));
    var pseudonymizeResponse = new PseudonymizeResponse(tidMap, Duration.ofDays(1));
    tca.when(mockRequestSpec)
        .respond(successResponse(200, om.writeValueAsString(pseudonymizeResponse)));
  }

  public void isDown() {
    tca.when(mockRequestSpec).error(HttpError.error().withDropConnection(true));
  }

  public void timeout() {
    tca.when(mockRequestSpec).respond(request -> null, Delay.minutes(10));
  }

  public void unknownDomain(ObjectMapper om) throws JsonProcessingException {
    tca.when(mockRequestSpec)
        .respond(
            successResponse(
                400,
                om.writeValueAsString(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "Unknown domain 'MII'"))));
  }

  public void successWithStatusCode(List<Integer> statusCodes) throws JsonProcessingException {
    var tidMap = transportIds.stream().collect(toMap(Function.identity(), Function.identity()));
    var pseudonymizeResponse =
        om.writeValueAsString(new PseudonymizeResponse(tidMap, Duration.ofDays(1)));
    var rs = new LinkedList<>(statusCodes);
    tca.when(mockRequestSpec)
        .respond(
            request ->
                Optional.ofNullable(rs.poll())
                    .map(
                        statusCode -> {
                          log.trace("statusCode: {}", statusCode);
                          return statusCode < 400
                              ? successResponse(200, pseudonymizeResponse)
                              : response().withStatusCode(statusCode);
                        })
                    .orElseGet(() -> successResponse(200, pseudonymizeResponse)));
  }

  private HttpResponse successResponse(int statusCode, String om) {
    return response().withStatusCode(statusCode).withContentType(APPLICATION_JSON).withBody(om);
  }
}
