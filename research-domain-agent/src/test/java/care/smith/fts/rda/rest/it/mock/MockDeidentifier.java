package care.smith.fts.rda.rest.it.mock;

import static care.smith.fts.test.MockServerUtil.connectionReset;
import static care.smith.fts.test.MockServerUtil.delayedResponse;
import static care.smith.fts.test.MockServerUtil.sequentialMock;
import static care.smith.fts.test.TidPidMap.getTidPidMapAsJson;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

import care.smith.fts.util.tca.ResearchMappingResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class MockDeidentifier {

  public static final String SCENARIO_NAME = "tcaSequentialRequests";
  private final ObjectMapper om;
  private final WireMock tca;

  public MockDeidentifier(ObjectMapper om, WireMockServer tca) {
    this.om = om;
    this.tca = new WireMock(tca);
  }

  public void success() throws IOException {
    success(List.of());
  }

  public void success(List<Integer> statusCodes) throws IOException {
    var tidPidMap = om.readValue(getTidPidMapAsJson(), new TypeReference<Map<String, String>>() {});
    var resolveResponse = new ResearchMappingResponse(tidPidMap, Duration.ofMillis(12345));
    var body = om.writeValueAsString(resolveResponse);

    var request =
        post("/api/v2/rd/research-mapping")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
    var seq = sequentialMock(tca);
    for (int statusCode : statusCodes) {
      ResponseDefinitionBuilder response;
      response = statusCode < 400 ? jsonResponse(body, statusCode) : status(statusCode);
      seq = seq.then(request, response);
    }
    seq.thereafter(request, jsonResponse(body, 200));
  }

  public void isDown() {
    tca.register(any(ANY).willReturn(connectionReset()));
  }

  public void hasTimeout() {
    tca.register(any(ANY).willReturn(delayedResponse()));
  }

  public void returnsWrongContentType() {
    tca.register(
        post("/api/v2/rd/research-mapping")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(ok().withHeader(CONTENT_TYPE, "text/plain")));
  }
}
