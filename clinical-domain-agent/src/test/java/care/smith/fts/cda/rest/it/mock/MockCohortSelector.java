package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.test.FhirGenerators.withPrefix;
import static care.smith.fts.test.MockServerUtil.connectionReset;
import static care.smith.fts.test.MockServerUtil.delayedResponse;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.sequentialMock;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.FhirUtils.typedResourceStream;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static com.google.common.collect.Lists.partition;
import static java.lang.Math.ceilDiv;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@Slf4j
public class MockCohortSelector {

  private final WireMock tca;
  private final String basePath;

  public static MockCohortSelector fetchAll(WireMockServer tca) {
    return new MockCohortSelector(tca, "/api/v2/cd/consented-patients/fetch-all");
  }

  public static MockCohortSelector fetch(WireMockServer tca) {
    return new MockCohortSelector(tca, "/api/v2/cd/consented-patients/fetch");
  }

  public MockCohortSelector(WireMockServer tca, String basePath) {
    this.tca = new WireMock(tca);
    this.basePath = basePath;
  }

  private FhirGenerator<Bundle> validConsent(Supplier<String> patientId) {
    try {
      return FhirGenerators.gicsResponse(patientId);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void consentForOnePatient(String patientId) {
    consentForNPatients(patientId, 1);
  }

  public void consentForNPatients(String idPrefix, int total) {
    consentForNPatientsWithPaging(idPrefix, total, total);
  }

  public void consentForNPatients(String idPrefix, int total, List<Integer> statusCodes) {
    consentForNPatientsWithPaging(idPrefix, total, total, statusCodes);
  }

  public void consentForNPatientsWithPaging(String idPrefix, int total, int maxPageSize) {

    var statusCodes = Stream.generate(() -> 200).limit(ceilDiv(total, maxPageSize)).toList();
    consentForNPatientsWithPaging(idPrefix, total, maxPageSize, statusCodes);
  }

  public void consentForNPatientsWithPaging(
      String idPrefix, int total, int maxPageSize, List<Integer> statusCodes) {
    assertThat(maxPageSize).isGreaterThan(0);
    assertThat(total).isGreaterThanOrEqualTo(maxPageSize);

    var consents = generateConsents(idPrefix, total);
    var pages = new LinkedList<>(generatePages(consents, maxPageSize));
    var seq = sequentialMock(tca);

    var request = post(urlPathEqualTo(basePath));
    var butLast = statusCodes.subList(0, statusCodes.size() - 1);
    for (var statusCode : butLast) {
      var response =
          statusCode < 400
              // May be out of bounds, meaning there is not enough consent for the
              // number of statuses
              ? successResponse(pages.poll(), statusCode)
              : status(statusCode);
      log.debug("Generate {} response", statusCode);
      seq = seq.then(request, response);
    }
    log.debug("Thereafter respond with {}", statusCodes.getLast());
    seq.thereafter(request, successResponse(pages.poll(), statusCodes.getLast()));
  }

  /**
   * Generates a list of paginated Bundle objects from the given list of consent Bundles based on
   * the specified maximum page size.
   *
   * <p>Each page is created by partitioning the input list into sublists of the given maximum size,
   * wrapping these sublists into a Bundle, setting the total number of consents, and then linking
   * the pages together with appropriate navigation links.
   *
   * @param consents the list of input consent Bundles to be partitioned into pages
   * @param maxPageSize the maximum number of Bundles allowed per page
   * @return a list of Bundle objects representing the paginated content
   */
  private List<Bundle> generatePages(List<Bundle> consents, int maxPageSize) {
    var pages =
        new LinkedList<>(partition(consents, maxPageSize))
            .stream()
                .map(c -> c.stream().collect(toBundle()))
                .map(b -> b.setTotal(consents.size()))
                .toList();
    var butLast = pages.subList(0, pages.size() - 1);
    var last = pages.getLast();
    return Streams.concat(
            range(0, butLast.size())
                .mapToObj(
                    page -> butLast.get(page).addLink(nextLink(++page * maxPageSize, maxPageSize))),
            Stream.of(last))
        .toList();
  }

  /**
   * Generates a list of FHIR Bundle resources based on a given prefix and a specified total count.
   * The generated resources will either use the provided prefix directly or a generated prefix if
   * multiple items are required.
   */
  private List<Bundle> generateConsents(String idPrefix, int total) {
    return validConsent(total > 1 ? withPrefix(idPrefix) : () -> idPrefix)
        .generateResources()
        .limit(total)
        .toList();
  }

  /**
   * Generates a success response for the given FHIR Bundle with the specified HTTP status code.
   * This method creates a response containing the bundle converted to a JSON string, and sets
   * appropriate headers and status code for the response.
   *
   * @param bundle the FHIR Bundle to include in the response body
   * @param statusCode the HTTP status code for the response
   * @return a ResponseDefinitionBuilder configured with the provided bundle and status code
   */
  private ResponseDefinitionBuilder successResponse(Bundle bundle, int statusCode) {
    log.trace(
        "Returning consent for {}",
        typedResourceStream(bundle, Bundle.class)
            .flatMap(b -> typedResourceStream(b, Patient.class))
            .map(Resource::getId)
            .toList());
    return fhirResponse(bundle, statusCode);
  }

  /**
   * Creates a "next" link component for a FHIR Bundle.
   *
   * @param from the starting index for the next set of results
   * @param count the number of results to be retrieved in the next set
   * @return a Bundle.BundleLinkComponent representing the "next" link
   */
  private Bundle.BundleLinkComponent nextLink(int from, int count) {
    var uri =
        fromUriString(basePath)
            .replaceQueryParam("from", from)
            .replaceQueryParam("count", count)
            .toUriString();
    log.trace("Next link uri: {}", uri);
    return new Bundle.BundleLinkComponent(new StringType("next"), new UriType(uri));
  }

  /**
   * Simulates a scenario where the system/network connection is unavailable by responding with a
   * connection reset fault.
   *
   * <p>This is used to mimic connectivity issues such as the remote server abruptly terminating the
   * connection.
   */
  public void isDown() {
    tca.register(any(ANY).willReturn(connectionReset()));
  }

  /**
   * Simulates a timeout scenario for HTTP requests.
   *
   * <p>This method configures a mock server to simulate a timeout by introducing a fixed delay of
   * 10 minutes (600,000 milliseconds) before returning an HTTP response with a status code of 204.
   * Useful for testing timeout handling mechanisms in applications.
   */
  public void timeout() {
    tca.register(post(ANY).willReturn(delayedResponse()));
  }

  /**
   * Simulates a scenario where a response contains the wrong content type. This method registers a
   * mocked HTTP POST request with a content type of "text/plain" instead of the expected content
   * type, which is typically used for FHIR resources.
   *
   * <p>The method constructs a mock FHIR response bundled into a String and then configures the
   * response with a 200 status code and the incorrect content type header. This setup is primarily
   * used for testing the handling of unexpected or invalid content types in HTTP responses.
   */
  public void wrongContentType() {
    var consent = Stream.of(validConsent(() -> "id1").generateResource()).collect(toBundle());
    var response =
        ok().withHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE).withBody(fhirResourceToString(consent));
    tca.register(post(urlPathEqualTo(basePath)).willReturn(response));
  }

  /**
   * Registers a mock POST request to simulate an HTTP 400 Bad Request error with a specific
   * ProblemDetail message indicating no consents were found for the specified domain.
   *
   * @param om the {@link ObjectMapper} used to serialize the ProblemDetail object into a JSON
   *     string for the mock response body
   * @throws JsonProcessingException if an error occurs during JSON serialization
   */
  public void unknownDomain(ObjectMapper om) throws JsonProcessingException {
    String body =
        om.writeValueAsString(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "No consents found for domain  'MII1234'"));
    tca.register(post(urlPathEqualTo(basePath)).willReturn(jsonResponse(body, 400)));
  }
}
