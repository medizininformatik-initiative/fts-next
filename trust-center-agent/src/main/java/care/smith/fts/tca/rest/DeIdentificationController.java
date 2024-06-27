package care.smith.fts.tca.rest;

import care.smith.fts.tca.deidentification.PseudonymProvider;
import care.smith.fts.tca.deidentification.ShiftedDatesProvider;
import care.smith.fts.util.tca.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    return response.map(r -> new ResponseEntity<>(r, HttpStatus.OK));
  }

  @PostMapping(
      value = "/rd/resolve-pseudonyms",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, String>>> fetchPseudonymizedIds(
      @Validated(TransportIdsRequest.class) @RequestBody Mono<TransportIdsRequest> requestData) {
    var pseudonymizedIDs =
        requestData
            .doOnNext(b -> log.info("ids: %s, domain: %s".formatted(b.ids(), b.domain())))
            .flatMap(pseudonymProvider::fetchPseudonymizedIds);
    return pseudonymizedIDs.map(r -> new ResponseEntity<>(r, HttpStatus.OK));
  }

  @PostMapping(
      value = "/rd/delete-transport-ids",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Long>> deleteTransportIds(
      @Validated(TransportIdsRequest.class) @RequestBody Mono<TransportIdsRequest> requestData) {
    var response = requestData.flatMap(pseudonymProvider::deleteTransportIds);
    return response.map(r -> new ResponseEntity<>(r, HttpStatus.OK));
  }
}
