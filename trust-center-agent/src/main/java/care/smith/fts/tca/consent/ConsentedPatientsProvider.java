package care.smith.fts.tca.consent;

import care.smith.fts.util.tca.ConsentRequest;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

public interface ConsentedPatientsProvider {
  Mono<Bundle> fetchAll(
      ConsentRequest consentRequest, UriComponentsBuilder requestUrl, PagingParams pagingParams);

  Mono<Bundle> fetch(
      ConsentRequest consentRequest,
      UriComponentsBuilder requestUrl,
      PagingParams pagingParams,
      List<String> pids);

  record PagingParams(int from, int count) {
    public PagingParams {
      if (from < 0 || count < 0) {
        throw new IllegalArgumentException("from and count must be non-negative");
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
