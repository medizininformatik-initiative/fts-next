package care.smith.fts.cda.impl;

import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.ConsentedPatient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

class TCACohortSelector implements CohortSelector {
  private final ObjectMapper objectMapper;
  private final TCACohortSelectorConfig config;
  private final CloseableHttpClient client;

  public TCACohortSelector(
      TCACohortSelectorConfig config, ObjectMapper objectMapper, CloseableHttpClient client) {
    this.objectMapper = objectMapper;
    this.config = config;
    this.client = client;
  }

  @Override
  public List<ConsentedPatient> selectCohort() {
    var uri = config.server().baseUrl() + "/api/v1/cd/consent-request";
    var request = new HttpPost(uri);
    request.setEntity(policyRequest(config.policies(), config.domain()));
    return fetchConsent(request);
  }

  private List<ConsentedPatient> fetchConsent(HttpUriRequest request) {
    try {
      return client.execute(request, this::readConsentResponse);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to fetch response", e);
    }
  }

  private List<ConsentedPatient> readConsentResponse(ClassicHttpResponse response) {
    try {
      if (response.getCode() >= 400) {
        throw new IllegalStateException("Server returned an error: " + response.getCode());
      }
      return objectMapper.readValue(response.getEntity().getContent(), new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to parse response", e);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read entity from response", e);
    }
  }

  private StringEntity policyRequest(List<String> policies, String domain) {
    try {
      var request = Map.of("policies", policies, "domain", domain);
      var requestAsString = objectMapper.writeValueAsString(request);
      return new StringEntity(requestAsString, ContentType.APPLICATION_JSON);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to convert policies to JSON", e);
    }
  }
}
