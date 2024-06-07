package care.smith.fts.tca.rest;

import care.smith.fts.tca.deidentification.PseudonymProvider;
import care.smith.fts.tca.deidentification.ShiftedDatesProvider;
import care.smith.fts.util.tca.*;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

  @PostMapping(value = "/cd/transport-ids")
  public ResponseEntity<TransportIDs> getTransportId(
      @Validated(PseudonymRequest.class) @RequestBody PseudonymRequest requestData)
      throws IOException {
    TransportIDs response = pseudonymProvider.retrieveTransportIds(requestData);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping(value = "/cd/shifted-dates")
  public ResponseEntity<ShiftedDates> getShiftedDates(
      @Valid @RequestBody DateShiftingRequest requestData) {
    ShiftedDates response = shiftedDatesProvider.generateShiftedDates(requestData);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping(value = "/rd/resolve-pseudonyms")
  public ResponseEntity<PseudonymizedIDs> fetchPseudonymizedIds(
      @Validated(PseudonymRequest.class) @RequestBody PseudonymRequest requestData)
      throws IOException {
    PseudonymizedIDs pseudonymizedIDs = pseudonymProvider.fetchPseudonymizedIds(requestData);
    return new ResponseEntity<>(pseudonymizedIDs, HttpStatus.OK);
  }

  @PostMapping(value = "/rd/resolve-project-pseudonyms")
  public ResponseEntity<PseudonymizedIDs> fetchProjectPseudonymizedIds(
      @Validated(PseudonymRequest.class) @RequestBody PseudonymRequest requestData)
      throws IOException {
    // TODO Implement
    // PseudonymizedIDs pseudonymizedIDs =
    // pseudonymProvider.fetchProjectPseudonymizedIds(requestData);
    return ResponseEntity.internalServerError().build();
  }

  @PostMapping(value = "/rd/delete-transport-ids")
  public ResponseEntity<String> deleteId(
      @Validated(PseudonymRequest.class) @RequestBody PseudonymRequest requestData) {
    pseudonymProvider.deleteTransportId(requestData);
    return ResponseEntity.ok().build();
  }
}
