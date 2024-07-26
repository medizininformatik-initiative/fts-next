package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.test.FhirGenerators.withPrefix;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.toBundle;
import static java.lang.Math.min;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.util.MediaTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class MockCohortSelector {

  private final MockServerClient tca;

  public MockCohortSelector(MockServerClient tca) {
    this.tca = tca;
  }

  private FhirGenerator<Bundle> validConsent(Supplier<String> patientId) throws IOException {
    return FhirGenerators.gicsResponse(patientId);
  }

  public void consentForOnePatient(String patientId) throws IOException {
    log.info("consentForOnePatient {}", patientId);
    consentForNPatients(patientId, 1);
  }

  public void consentForNPatients(String idPrefix, int n) throws IOException {
    consentForNPatientsWithPaging(idPrefix, n, n);
  }

  public void consentForNPatients(String idPrefix, int n, List<Integer> statusCodes)
      throws IOException {
    consentForNPatientsWithPaging(idPrefix, n, n, statusCodes);
  }

  public void consentForNPatientsWithPaging(String idPrefix, int total, int maxPageSize)
      throws IOException {
    consentForNPatientsWithPaging(idPrefix, total, maxPageSize, List.of());
  }

  public void consentForNPatientsWithPaging(
      String idPrefix, int total, int maxPageSize, List<Integer> statusCodes) throws IOException {
    assertThat(maxPageSize).isGreaterThan(0);
    assertThat(total).isGreaterThanOrEqualTo(maxPageSize);

    var bundleFhirGenerator =
        total > 1 ? validConsent(withPrefix(idPrefix)) : validConsent(() -> idPrefix);
    var rs = new LinkedList<>(statusCodes);

    log.trace("total: {}, maxPageSize: {}", total, maxPageSize);

    tca.when(request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            request -> {
              log.trace("path: {}", request.getPath());
              return Optional.ofNullable(rs.poll())
                  .map(
                      statusCode -> {
                        log.trace("statusCode: {}", statusCode);
                        return statusCode < 400
                            ? successResponse(
                                total, maxPageSize, request, bundleFhirGenerator, statusCode)
                            : response().withStatusCode(statusCode);
                      })
                  .orElseGet(
                      () -> successResponse(total, maxPageSize, request, bundleFhirGenerator, 200));
            });
  }

  private HttpResponse successResponse(
      int total,
      int maxPageSize,
      HttpRequest request,
      FhirGenerator<Bundle> bundleFhirGenerator,
      int statusCode) {
    log.trace("Generate response with status code: {}", statusCode);
    var from = extractParameter(request, "from", 0);
    var count = extractParameter(request, "count", maxPageSize);
    log.trace("request params: from {}, count {}", from, count);

    var consent =
        bundleFhirGenerator.generateResources().limit(count).collect(toBundle()).setTotal(total);

    consent = addNextLink(total, maxPageSize, from, count, consent);
    return response()
        .withStatusCode(statusCode)
        .withContentType(MediaType.parse(MediaTypes.APPLICATION_FHIR_JSON_VALUE))
        .withBody(fhirResourceToString(consent));
  }

  private static int extractParameter(HttpRequest request, String name, int x) {
    String fromRequest = request.getFirstQueryStringParameter(name);
    return !fromRequest.isEmpty() ? Integer.parseInt(fromRequest) : x;
  }

  private Bundle addNextLink(int total, int maxPageSize, int from, int count, Bundle consent) {
    var nextFrom = from + count;
    var nextCount = min(total - nextFrom, maxPageSize);
    if (nextFrom < total) {
      log.trace("Add nextLink count consent bundle");
      consent = consent.addLink(nextLink(nextFrom, nextCount));
    }
    return consent;
  }

  private Bundle.BundleLinkComponent nextLink(int from, int count) {

    var uri =
        UriComponentsBuilder.fromUriString(
                "http://localhost:%s/api/v2/cd/consented-patients".formatted(tca.getPort()))
            .replaceQueryParam("from", from)
            .replaceQueryParam("count", count)
            .toUriString();

    log.trace("Next link uri: {}", uri);

    return new Bundle.BundleLinkComponent(new StringType("next"), new UriType(uri));
  }

  public void isDown() {
    tca.when(request()).error(HttpError.error().withDropConnection(true));
  }

  public void timeout() {
    tca.when(request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(request -> null, Delay.minutes(10));
  }

  public void wrongContentType() throws IOException {
    var consent = Stream.of(validConsent(() -> "id1").generateResource()).collect(toBundle());
    tca.when(request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.PLAIN_TEXT_UTF_8)
                .withBody(fhirResourceToString(consent)));
  }

  public void unknownDomain(ObjectMapper om) throws JsonProcessingException {
    tca.when(request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            response()
                .withStatusCode(400)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(
                    om.writeValueAsString(
                        ProblemDetail.forStatusAndDetail(
                            HttpStatus.BAD_REQUEST, "No consents found for domain  'MII1234'"))));
  }
}
