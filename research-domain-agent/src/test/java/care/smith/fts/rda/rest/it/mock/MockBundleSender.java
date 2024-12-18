package care.smith.fts.rda.rest.it.mock;

import static care.smith.fts.test.MockServerUtil.connectionReset;
import static care.smith.fts.test.MockServerUtil.delayedResponse;
import static care.smith.fts.test.MockServerUtil.sequentialMock;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;

public class MockBundleSender {

  public static final String SCENARIO_NAME = "hdsSequentialRequests";
  private final WireMock hds;
  private final MappingBuilder request;

  public MockBundleSender(WireMockServer hds) {
    this.hds = new WireMock(hds);
    request =
        post(ANY)
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON_VALUE))
            .withRequestBody(equalToJson("{\"resourceType\":\"Bundle\"}", true, true));
  }

  public void success() {
    success(List.of());
  }

  public void success(List<Integer> statusCodes) {
    var seq = sequentialMock(hds);
    for (int statusCode : statusCodes) {
      seq = seq.then(request, status(statusCode));
    }
    seq.thereafter(request, ok());
  }

  public void isDown() {
    hds.register(any(ANY).willReturn(connectionReset()));
  }

  public void hasTimeout() {
    hds.register(any(ANY).willReturn(delayedResponse()));
  }
}
