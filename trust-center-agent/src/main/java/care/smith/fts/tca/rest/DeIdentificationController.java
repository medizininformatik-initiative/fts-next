package care.smith.fts.tca.rest;

import care.smith.fts.tca.deidentification.PseudonymProvider;
import care.smith.fts.tca.deidentification.ShiftedDatesProvider;
import care.smith.fts.util.error.ErrorResponseUtil;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
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
@Validated
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
      @Valid @RequestBody Mono<PseudonymizeRequest> requestData) {
    var response =
        requestData.flatMap(
            r -> {
              if (!r.ids().isEmpty()) {
                var transportIds =
                    pseudonymProvider.retrieveTransportIds(r.patientId(), r.ids(), r.domain());
                var shiftedDate =
                    shiftedDatesProvider.generateDateShift(r.patientId(), r.dateShift());
                return transportIds.zipWith(
                    shiftedDate,
                    (t, shift) -> new PseudonymizeResponse(t.getT1(), t.getT2(), shift));
              } else {
                return Mono.empty();
              }
            });

    return response
        .map(ResponseEntity::ok)
        .onErrorResume(
            e -> {
              if (e instanceof UnknownDomainException) {
                return ErrorResponseUtil.badRequest(e);
              } else {
                log.error("Unexpected error", e);
                return ErrorResponseUtil.internalServerError(e);
              }
            });
  }

  @PostMapping(
      value = "/rd/resolve-pseudonyms",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, String>>> fetchPseudonymizedIds(
      @RequestBody @NotNull @Pattern(regexp = "^[\\w-]+$") String transportIDMapName) {
    log.trace("Resolve pseudonyms of map: {} ", transportIDMapName);
    return pseudonymProvider
        .fetchPseudonymizedIds(transportIDMapName)
        .doOnError(
            e ->
                log.error(
                    "Could not fetch pseudonyms of map {}: {}", transportIDMapName, e.getMessage()))
        .map(ResponseEntity::ok);
  }
}
