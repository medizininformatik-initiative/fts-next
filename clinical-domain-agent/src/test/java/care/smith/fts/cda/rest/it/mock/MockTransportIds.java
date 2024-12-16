package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.test.MockServerUtil.connectionReset;
import static care.smith.fts.test.MockServerUtil.delayedResponse;
import static care.smith.fts.test.MockServerUtil.sequentialMock;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static java.time.Duration.ofDays;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.util.tca.TransportMappingResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@Slf4j
@Builder
public class MockTransportIds {

  private final ObjectMapper om;
  private final WireMock tca;
  private final MappingBuilder mockRequestSpec;
  private final Set<String> transportIds;

  public MockTransportIds(
      ObjectMapper om, WireMock tca, MappingBuilder mockRequestSpec, Set<String> transportIds) {
    this.om = om;
    this.tca = tca;
    this.mockRequestSpec = mockRequestSpec;
    this.transportIds = transportIds;
  }

  public void success() throws JsonProcessingException {
    var transportMapping = transportIds.stream().collect(toMap(identity(), identity()));
    var pseudonymizeResponse =
        new TransportMappingResponse("transferId", transportMapping, ofDays(1));
    tca.register(
        mockRequestSpec.willReturn(jsonResponse(om.writeValueAsString(pseudonymizeResponse), 200)));
  }

  public void isDown() {
    tca.register(mockRequestSpec.willReturn(connectionReset()));
  }

  public void timeout() {
    tca.register(mockRequestSpec.willReturn(delayedResponse()));
  }

  public void unknownDomain(ObjectMapper om) throws JsonProcessingException {
    tca.register(
        mockRequestSpec.willReturn(
            jsonResponse(
                om.writeValueAsString(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "Unknown domain 'MII'")),
                400)));
  }

  public void successWithStatusCode(List<Integer> statusCodes) throws JsonProcessingException {
    var tidMap = transportIds.stream().collect(toMap(identity(), identity()));
    var pseudonymizeResponse =
        om.writeValueAsString(new TransportMappingResponse("transferId", tidMap, ofDays(1)));

    var seq = sequentialMock(tca);
    for (var statusCode : statusCodes) {
      seq.then(mockRequestSpec, statusCode < 400 ? ok(pseudonymizeResponse) : status(statusCode));
    }
    seq.thereafter(mockRequestSpec, jsonResponse(pseudonymizeResponse, 200));
  }
}
