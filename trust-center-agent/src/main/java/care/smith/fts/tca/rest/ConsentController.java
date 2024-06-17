package care.smith.fts.tca.rest;

import care.smith.fts.tca.consent.ConsentProvider;
import care.smith.fts.util.tca.ConsentRequest;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(value = "api/v2")
public class ConsentController {
  private final ConsentProvider consentProvider;
  private final int defaultPageSize;

  @Autowired
  ConsentController(
      ConsentProvider consentProvider, @Qualifier("defaultPageSize") int defaultPageSize) {
    this.consentProvider = consentProvider;
    this.defaultPageSize = defaultPageSize;
  }

  @GetMapping(
      value = "/cd/consented-patients",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Mono<Bundle>> consentedPatients(
      @Validated(ConsentRequest.class) @RequestBody ConsentRequest request,
      @RequestParam Optional<Integer> from,
      @RequestParam Optional<Integer> count) {
    log.info("consentedPatients: {}", request);
    var consentedPatients =
        consentProvider.consentedPatientsPage(
            request.getDomain(),
            request.getPolicySystem(),
            request.getPolicies(),
            from.orElse(0),
            count.orElse(defaultPageSize));
    return new ResponseEntity<>(consentedPatients, HttpStatus.OK);
  }
}
