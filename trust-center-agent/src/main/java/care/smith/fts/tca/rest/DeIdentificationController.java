package care.smith.fts.tca.rest;

import care.smith.fts.tca.deidentification.PseudonymProvider;
import care.smith.fts.tca.deidentification.ShiftedDatesProvider;
import care.smith.fts.util.tca.*;
import care.smith.fts.util.tca.IDMap;
import jakarta.validation.Valid;
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
              Mono<IDMap> transportIds =
                  pseudonymProvider.retrieveTransportIds(r.getIds(), r.getDomain());
              Mono<ShiftedDates> shiftedDates =
                  shiftedDatesProvider.generateDateShift(
                      Set.of(r.getPatientId()), r.getDateShift());
              return transportIds.zipWith(
                  shiftedDates, (t, s) -> new PseudonymizeResponse(t, s.get(r.getPatientId())));
            });
    return response.map(r -> new ResponseEntity<>(r, HttpStatus.OK));
  }

  @PostMapping(
      value = "/cd/transport-ids",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<IDMap>> getTransportId(
      @Validated(TransportIdsRequest.class) @RequestBody Mono<TransportIdsRequest> requestData) {
    var response =
        requestData.flatMap(r -> pseudonymProvider.retrieveTransportIds(r.getIds(), r.getDomain()));
    return response.map(r -> new ResponseEntity<>(r, HttpStatus.OK));
  }

  @PostMapping(
      value = "/cd/shifted-dates",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<ShiftedDates>> getShiftedDates(
      @Valid @RequestBody Mono<DateShiftingRequest> requestData) {
    var response =
        requestData.flatMap(
            r -> shiftedDatesProvider.generateDateShift(r.getIds(), r.getDateShift()));
    return response.map(r -> new ResponseEntity<>(r, HttpStatus.OK));
  }

  @PostMapping(
      value = "/rd/resolve-pseudonyms",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<IDMap>> fetchPseudonymizedIds(
      @Validated(TransportIdsRequest.class) @RequestBody Mono<TransportIdsRequest> requestData) {
    var pseudonymizedIDs = requestData.flatMap(pseudonymProvider::fetchPseudonymizedIds);
    return pseudonymizedIDs.map(r -> new ResponseEntity<>(r, HttpStatus.OK));
  }

  @PostMapping(
      value = "/rd/resolve-project-pseudonyms",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IDMap> fetchProjectPseudonymizedIds(
      @Validated(TransportIdsRequest.class) @RequestBody TransportIdsRequest requestData) {
    // TODO Implement
    // IDMap pseudonymizedIDs =
    // pseudonymProvider.fetchProjectPseudonymizedIds(requestData);
    return ResponseEntity.internalServerError().build();
  }

  @PostMapping(
      value = "/rd/delete-transport-ids",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  public Mono<ResponseEntity<Void>> deleteId(
      @Validated(TransportIdsRequest.class) @RequestBody Mono<TransportIdsRequest> requestData) {
    var response = requestData.flatMap(pseudonymProvider::deleteTransportId);
    return response.map(r -> new ResponseEntity<>(r, HttpStatus.OK));
  }
}
