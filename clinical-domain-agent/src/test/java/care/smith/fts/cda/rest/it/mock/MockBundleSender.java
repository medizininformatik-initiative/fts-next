package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.test.MockServerUtil.accepted;
import static care.smith.fts.test.MockServerUtil.connectionReset;
import static care.smith.fts.test.MockServerUtil.delayedResponse;
import static care.smith.fts.test.MockServerUtil.sequentialMock;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockBundleSender {

  private static final String BASE_PATH = "/api/v2/process";

  private final WireMock rda;
  private final MappingBuilder request;

  public MockBundleSender(WireMockServer rda) {
    this.rda = new WireMock(rda);

    request =
        post(urlPathEqualTo(BASE_PATH + "/test/patient"))
            .withRequestBody(equalToJson("{\"resourceType\":\"Bundle\",\"total\":2}", true, true));
  }

  public void success() {
    rda.register(request.willReturn(initialSuccessResponse()));

    rda.register(get(urlPathEqualTo(BASE_PATH + "/status/processId456")).willReturn(ok()));
  }

  public void successWithStatusCode(List<Integer> statusCodes) {
    var seq = sequentialMock(rda);
    var butLast = statusCodes.subList(0, statusCodes.size() - 1);
    for (var statusCode : butLast) {
      var response = statusCode < 400 ? initialSuccessResponse() : status(statusCode);
      seq = seq.then(request, response);
    }
    seq.thereafter(request, initialSuccessResponse());

    rda.register(get(urlPathEqualTo(BASE_PATH + "/status/processId456")).willReturn(ok()));
  }

  private ResponseDefinitionBuilder initialSuccessResponse() {
    log.trace("initialSuccessResponse with statusCode 200");
    return ok().withHeader(CONTENT_LOCATION, BASE_PATH + "/status/processId456");
  }

  public void isDown() {
    rda.register(request.willReturn(connectionReset()));
  }

  public void timeout() {
    rda.register(request.willReturn(delayedResponse()));
  }

  public void successWithRetryAfter() {
    rda.register(request.willReturn(initialSuccessResponse()));

    var statusRequest = get(urlPathEqualTo(BASE_PATH + "/status/processId456"));
    sequentialMock(rda)
        .then(statusRequest, accepted().withHeader(RETRY_AFTER, "2"))
        .then(statusRequest, accepted().withHeader(RETRY_AFTER, "1"))
        .thereafter(statusRequest, ok());
  }
}
