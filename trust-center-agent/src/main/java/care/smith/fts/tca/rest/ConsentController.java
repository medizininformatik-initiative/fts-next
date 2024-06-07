package care.smith.fts.tca.rest;

import care.smith.fts.util.tca.ConsentRequest;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.tca.consent.ConsentProvider;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "api/v2")
public class ConsentController {
  private final ConsentProvider consentProvider;

  @Autowired
  ConsentController(ConsentProvider consentProvider) {
    this.consentProvider = consentProvider;
  }

  @GetMapping(
      value = "/cd/all-consented-patients",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ConsentedPatient>> allConsentedPatients(
      @Validated(ConsentRequest.class) @RequestBody ConsentRequest request) throws IOException {
    log.info("Get all ConsentedPatients: {}", request);
    List<ConsentedPatient> consentedPatients =
        consentProvider.allConsentedPatients(request.getDomain(), request.getPolicies());
    log.info("Found {} consented patients", consentedPatients.size());
    return new ResponseEntity<>(consentedPatients, HttpStatus.OK);
  }
}
