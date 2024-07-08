package care.smith.fts.cda.rest.it.mock;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.PseudonymizeRequest;
import care.smith.fts.util.tca.PseudonymizeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.mockserver.client.MockServerClient;

public class MockTransportIds {

  private final MockServerClient tca;

  public MockTransportIds(MockServerClient tca) {
    this.tca = tca;
  }

  public void success(ObjectMapper om, String patientId, String identifierSystem)
      throws JsonProcessingException {
    var tid1 = patientId + ".identifier." + identifierSystem + ":" + patientId;
    var tid2 = patientId + ".id.Patient:" + patientId;

    var pseudonymizeRequest =
        new PseudonymizeRequest(patientId, Set.of(tid1, tid2), "MII", Duration.ofDays(14));
    PseudonymizeResponse pseudonymizeResponse =
        new PseudonymizeResponse(Map.of(tid1, "tid1", tid2, "tid2"), Duration.ofDays(1));
    tca.when(
            request()
                .withMethod("POST")
                .withContentType(APPLICATION_JSON)
                .withPath("/api/v2/cd/transport-ids-and-date-shifting-values")
                .withBody(json(om.writeValueAsString(pseudonymizeRequest))))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(APPLICATION_JSON)
                .withBody(om.writeValueAsString(pseudonymizeResponse)));
  }

  public void unknownDomain(ObjectMapper om) throws JsonProcessingException {
    var tid = "id1" + ".id.Patient:" + "id1";

    var pseudonymizeRequest =
        new PseudonymizeRequest("id1", Set.of(tid), "unknown domain", Duration.ofDays(14));

    tca.when(
            request()
                .withMethod("POST")
                .withContentType(APPLICATION_JSON)
                .withPath("/api/v2/cd/transport-ids-and-date-shifting-values")
                .withBody(json(om.writeValueAsString(pseudonymizeRequest))))
        .respond(
            response()
                .withStatusCode(400)
                .withBody(
                    om.writeValueAsString(
                        new UnknownDomainException("Unknown domain 'unknown domain'"))));
  }
}
