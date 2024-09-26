package care.smith.fts.tca.consent;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.util.tca.ConsentRequest;
import java.net.URI;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

interface GicsFhirRequestHelper<C extends ConsentRequest> {
  URI buildUri(UriBuilder uri, PagingParams pagingParams);

  Map<String, ?> buildBody(C req, PagingParams paging);

  Bundle processResponse(
      Bundle bundle, C req, UriComponentsBuilder requestUrl, PagingParams paging);

  String requestName();

  /**
   * Creates a "next" link component for pagination to the Bundle.
   *
   * <p>Constructs a URI for the next page of results using the provided request URL, paging
   * parameters, and path.
   *
   * @param requestUrl The base URL builder.
   * @param pagingParams Pagination details (sum and count).
   * @param path The path to append to the URL.
   * @return A Bundle.BundleLinkComponent with the "next" link URI.
   */
  static BundleLinkComponent nextLink(
      UriComponentsBuilder requestUrl, PagingParams pagingParams, String path) {
    var uri =
        requestUrl
            .path(path)
            .queryParam("from", pagingParams.sum())
            .replaceQueryParam("count", pagingParams.count())
            .toUriString();
    return new BundleLinkComponent(new StringType("next"), new UriType(uri));
  }
}
