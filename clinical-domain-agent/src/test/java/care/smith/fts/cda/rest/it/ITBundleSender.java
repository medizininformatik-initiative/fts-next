package care.smith.fts.cda.rest.it;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;

class ITBundleSender {

  private final MockServerClient rda;

  public ITBundleSender(MockServerClient rda) {
    this.rda = rda;
  }

  void success() {
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
}
