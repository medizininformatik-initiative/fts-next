package care.smith.fts.tca.rest;

import care.smith.fts.tca.deidentification.PseudonymProvider;
import care.smith.fts.tca.deidentification.ShiftedDatesProvider;
import care.smith.fts.util.tca.*;
import care.smith.fts.util.tca.IDMap;
import jakarta.validation.Valid;
import java.io.IOException;
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
  public ResponseEntity<PseudonymizeResponse> getTransportIdsAndDateShiftingValues(
      @Validated(PseudonymizeRequest.class) @RequestBody PseudonymizeRequest requestData)
      throws IOException {
    IDMap transportIds =
        pseudonymProvider.retrieveTransportIds(requestData.getIds(), requestData.getDomain());
    ShiftedDates shiftedDates =
        shiftedDatesProvider.generateDateShift(
            Set.of(requestData.getPatientId()), requestData.getDateShift());
    PseudonymizeResponse PseudonymizeResponse =
        new PseudonymizeResponse(transportIds, shiftedDates.get(requestData.getPatientId()));
    return new ResponseEntity<>(PseudonymizeResponse, HttpStatus.OK);
  }

  @PostMapping(
      value = "/cd/transport-ids",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IDMap> getTransportId(
      @Validated(TransportIdsRequest.class) @RequestBody TransportIdsRequest requestData)
      throws IOException {
    IDMap response =
        pseudonymProvider.retrieveTransportIds(requestData.getIds(), requestData.getDomain());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping(
      value = "/cd/shifted-dates",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ShiftedDates> getShiftedDates(
      @Valid @RequestBody DateShiftingRequest requestData) {
    ShiftedDates response =
        shiftedDatesProvider.generateDateShift(requestData.getIds(), requestData.getDateShift());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping(
      value = "/rd/resolve-pseudonyms",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IDMap> fetchPseudonymizedIds(
      @Validated(TransportIdsRequest.class) @RequestBody TransportIdsRequest requestData)
      throws IOException {
    IDMap pseudonymizedIDs = pseudonymProvider.fetchPseudonymizedIds(requestData);
    return new ResponseEntity<>(pseudonymizedIDs, HttpStatus.OK);
  }

  @PostMapping(
      value = "/rd/resolve-project-pseudonyms",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IDMap> fetchProjectPseudonymizedIds(
      @Validated(TransportIdsRequest.class) @RequestBody TransportIdsRequest requestData)
      throws IOException {
    // TODO Implement
    // IDMap pseudonymizedIDs =
    // pseudonymProvider.fetchProjectPseudonymizedIds(requestData);
    return ResponseEntity.internalServerError().build();
  }

  @PostMapping(
      value = "/rd/delete-transport-ids",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> deleteId(
      @Validated(TransportIdsRequest.class) @RequestBody TransportIdsRequest requestData) {
    pseudonymProvider.deleteTransportId(requestData);
    return ResponseEntity.ok().build();
  }
}
