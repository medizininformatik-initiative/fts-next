package care.smith.fts.rda.rest.it.mock;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
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
    hds.when(request).respond(response().withStatusCode(200));
  }
}
