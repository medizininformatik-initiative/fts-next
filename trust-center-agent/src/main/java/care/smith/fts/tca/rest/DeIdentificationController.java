package care.smith.fts.tca.rest;

import care.smith.fts.tca.deidentification.PseudonymProvider;
import care.smith.fts.tca.deidentification.ShiftedDatesProvider;
import care.smith.fts.util.error.ErrorResponseUtil;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(value = "api/v2")
public class DeIdentificationController {
  private final PseudonymProvider pseudonymProvider;
  private final ShiftedDatesProvider shiftedDatesProvider;

  @Autowired
  public DeIdentificationController(
      PseudonymProvider pseudonymProvider, ShiftedDatesProvider shiftedDatesProvider) {
    this.pseudonymProvider = pseudonymProvider;
    this.shiftedDatesProvider = shiftedDatesProvider;
  }

  @PostMapping(
      value = "/cd/transport-ids-and-date-shifting-values",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ExceptionHandler(UnknownDomainException.class)
  public Mono<ResponseEntity<PseudonymizeResponse>> getTransportIdsAndDateShiftingValues(
      @Validated(PseudonymizeRequest.class) @RequestBody Mono<PseudonymizeRequest> requestData) {
    var response =
        requestData.flatMap(
            r -> {
              if (!r.ids().isEmpty()) {
                Mono<Map<String, String>> transportIds =
                    pseudonymProvider.retrieveTransportIds(r.ids(), r.domain());
                Mono<Map<String, Duration>> shiftedDates =
                    shiftedDatesProvider.generateDateShift(Set.of(r.patientId()), r.dateShift());
                return transportIds.zipWith(
                    shiftedDates, (t, s) -> new PseudonymizeResponse(t, s.get(r.patientId())));
              } else {
                return Mono.empty();
              }
            });
    return response.map(ResponseEntity::ok).onErrorResume(ErrorResponseUtil::badRequest);
  }

  @PostMapping(
      value = "/rd/resolve-pseudonyms",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, String>>> fetchPseudonymizedIds(
      @RequestBody Mono<Set<String>> ids) {
    var pseudonymizedIDs =
        ids.doOnNext(b -> log.info("ids: {}", ids))
            .flatMap(pseudonymProvider::fetchPseudonymizedIds);
    return pseudonymizedIDs.map(ResponseEntity::ok);
  }

  @PostMapping(
      value = "/rd/delete-transport-ids",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Long>> deleteTransportIds(@RequestBody Mono<Set<String>> ids) {
    var response = ids.flatMap(pseudonymProvider::deleteTransportIds);
    return response.map(ResponseEntity::ok);
  }
}
