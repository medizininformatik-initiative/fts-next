package care.smith.fts.tca.rest;

import care.smith.fts.tca.consent.ConsentedPatientsProvider;
import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.util.MediaTypes;
import care.smith.fts.util.error.ErrorResponseUtil;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.ConsentRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(value = "api/v2")
@Validated
public class ConsentController {
  private final ConsentedPatientsProvider consentedPatientsProvider;
  private final int defaultPageSize;

  @Autowired
  ConsentController(
      ConsentedPatientsProvider consentedPatientsProvider,
      @Qualifier("defaultPageSize") int defaultPageSize) {
    this.consentedPatientsProvider = consentedPatientsProvider;
    this.defaultPageSize = defaultPageSize;
  }

  @PostMapping(
      value = "/cd/consented-patients",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaTypes.APPLICATION_FHIR_JSON_VALUE)
  public Mono<ResponseEntity<Bundle>> consentedPatients(
      @RequestBody @Valid Mono<ConsentRequest> request,
      UriComponentsBuilder uriBuilder,
      @RequestParam("from") Optional<Integer> from,
      @RequestParam("count") Optional<Integer> count) {
    var pagingParams = new PagingParams(from.orElse(0), count.orElse(defaultPageSize));
    var response =
        request.flatMap(r -> consentedPatientsProvider.fetchAll(r, uriBuilder, pagingParams));
    return response
        .map(ResponseEntity::ok)
        .onErrorResume(
            e -> {
              if (e instanceof UnknownDomainException) {
                return ErrorResponseUtil.badRequest(e);
              } else {
                return ErrorResponseUtil.internalServerError(e);
              }
            });
  }
}
