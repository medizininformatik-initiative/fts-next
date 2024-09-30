package care.smith.fts.tca.consent;

import static care.smith.fts.tca.consent.GicsFhirRequestHelper.nextLink;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

class FetchAllConsentsForDomain implements GicsFhirRequestHelper<ConsentFetchAllRequest> {
  @Override
  public URI buildUri(UriBuilder uri, PagingParams paging) {
    return uri.path("/$allConsentsForDomain")
        .queryParam("_count", paging.count())
        .queryParam("_offset", paging.from())
        .build();
  }

  @Override
  public Map<String, ?> buildBody(ConsentFetchAllRequest req, PagingParams paging) {
    return ofEntries(
        entry("resourceType", "Parameters"),
        entry("parameter", List.of(Map.of("name", "domain", "valueString", req.domain()))));
  }

  @Override
  public Bundle processResponse(
      Bundle bundle, ConsentFetchAllRequest req, UriComponentsBuilder url, PagingParams paging) {
    if (paging.sum() < bundle.getTotal()) {
      bundle.addLink(nextLink(url, paging, "/api/v2/cd/consented-patients/fetch-all"));
    }
    return bundle;
  }

  @Override
  public String requestName() {
    return "fetchAllConsentsForDomain";
  }
}
