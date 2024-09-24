package care.smith.fts.tca.consent;

import care.smith.fts.util.tca.ConsentRequest;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

public interface ConsentedPatientsProvider {

  /**
   * Fetch consent or all patient IDs provided by `consentRequest.pids()`.
   *
   * @param consentRequest
   * @param requestUrl
   * @param pagingParams
   * @return Mono<Bundle> with consented patients
   */
  Mono<Bundle> fetch(
      ConsentRequest consentRequest, UriComponentsBuilder requestUrl, PagingParams pagingParams);

  /**
   * Fetch consent for all patients in gICS.
   *
   * @param consentRequest
   * @param requestUrl
   * @param pagingParams
   * @return
   */
  Mono<Bundle> fetchAll(
      ConsentRequest consentRequest, UriComponentsBuilder requestUrl, PagingParams pagingParams);

  record PagingParams(int from, int count) {
    public PagingParams {
      if (from < 0 || count < 1) {
        throw new IllegalArgumentException("from must be non-negative and count greater than 0");
      } else if (Integer.MAX_VALUE - count < from) {
        throw new IllegalArgumentException(
            "from + count must be smaller than %s".formatted(Integer.MAX_VALUE));
      }
    }

    int sum() {
      return from + count;
    }

  }
}
