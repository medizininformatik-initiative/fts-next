package care.smith.fts.tca.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.util.tca.ConsentFetchRequest;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class FetchAllConsentsForPersonsTest {
  FetchAllConsentsForPersons fetchAllConsentsForPersons = new FetchAllConsentsForPersons();

  @Test
  void buildUri() {
    var uri =
        fetchAllConsentsForPersons.buildUri(
            fromUriString("http://trustcenteragent:8080"), new PagingParams(0, 100));
    assertThat(uri.getPath()).isEqualTo("/$allConsentsForPerson");
  }

  @Test
  void buildBody() {
    var req = buildRequest();
    var body = fetchAllConsentsForPersons.buildBody(req, new PagingParams(0, 100));
    var bodyString = body.toString();
    assertThat(bodyString.contains("domain")).isTrue();
    assertThat(bodyString.contains("patientIdentifierSystem")).isTrue();
    assertThat(bodyString.contains("id1")).isTrue();
    assertThat(bodyString.contains("id2")).isTrue();
  }

  @Test
  void tooLargeFromYieldsBodyWithoutPatientIds() {
    var req = buildRequest();

    var body = fetchAllConsentsForPersons.buildBody(req, new PagingParams(50, 100));
    var bodyString = body.toString();
    assertThat(bodyString.contains("domain")).isTrue();
    assertThat(bodyString.contains("patientIdentifierSystem")).isFalse();
    assertThat(bodyString.contains("id1")).isFalse();
    assertThat(bodyString.contains("id2")).isFalse();
  }

  @Test
  void processResponseAddsNextLink() {
    var req = buildRequest();
    var bundle =
        fetchAllConsentsForPersons.processResponse(
            new Bundle(),
            req,
            fromUriString("http://trustcenteragent:8008"),
            new PagingParams(0, 1));

    assertThat(bundle.hasLink()).isTrue();
  }

  @Test
  void processResponseAddsNoNextLink() {
    var req = buildRequest();
    var bundle =
        fetchAllConsentsForPersons.processResponse(
            new Bundle(),
            req,
            fromUriString("http://trustcenteragent:8008"),
            new PagingParams(0, 2));

    assertThat(bundle.hasLink()).isFalse();
  }

  private static @NotNull ConsentFetchRequest buildRequest() {
    return new ConsentFetchRequest(
        "domain", Set.of("poly"), "policySystem", "patientIdentifierSystem", List.of("id1", "id2"));
  }
}
