package care.smith.fts.tca.rest;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static care.smith.fts.tca.deidentification.DateShiftUtil.shiftDate;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_PREFIX;
import static java.util.Set.of;

import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.services.TransportIdService;
import care.smith.fts.util.error.ErrorResponseUtil;
import care.smith.fts.util.tca.TransportMappingResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Receives date mappings from CDA after FHIR Pseudonymizer processing, computes shifted dates, and
 * consolidates all mappings (identity tID→sID + date entries) into a single transferId-based
 * MapCache for RDA retrieval.
 */
@Slf4j
@RestController
@RequestMapping("api/v2")
@Validated
public class FpTransportMappingController {

  private final TransportIdService transportIdService;
  private final GpasClient gpasClient;

  public FpTransportMappingController(
      TransportIdService transportIdService, GpasClient gpasClient) {
    this.transportIdService = transportIdService;
    this.gpasClient = gpasClient;
  }

  @PostMapping("cd/fhir-pseudonymizer/transport-mapping")
  public Mono<ResponseEntity<TransportMappingResponse>> consolidateTransportMappings(
      @Valid @RequestBody FpTransportMappingRequest request) {

    log.debug(
        "Consolidating {} identity tIDs + {} date mappings for patient",
        request.transportIds().size(),
        request.dateMappings().size());

    return fetchDateShiftSeed(request)
        .map(seed -> computeShiftedDates(seed, request))
        .flatMap(dsEntries -> consolidate(request, dsEntries))
        .map(transferId -> ResponseEntity.ok(new TransportMappingResponse(transferId)))
        .onErrorResume(this::handleError);
  }

  private Mono<String> fetchDateShiftSeed(FpTransportMappingRequest request) {
    var seedKey = "%s_%s".formatted(request.maxDateShift().toString(), request.patientIdentifier());
    return gpasClient
        .fetchOrCreatePseudonyms(request.dateShiftDomain(), of(seedKey))
        .map(m -> m.get(seedKey));
  }

  private Map<String, String> computeShiftedDates(
      String seed, FpTransportMappingRequest request) {
    if (request.dateMappings().isEmpty()) {
      return Map.of();
    }

    var shift = generate(seed, request.maxDateShift(), request.dateShiftPreserve());

    return request.dateMappings().entrySet().stream()
        .collect(
            Collectors.toMap(
                e -> DATE_SHIFT_PREFIX + e.getKey(), e -> shiftDate(e.getValue(), shift)));
  }

  private Mono<String> consolidate(
      FpTransportMappingRequest request, Map<String, String> dsEntries) {
    var ttl = transportIdService.getDefaultTtl();
    return transportIdService.consolidateMappings(request.transportIds(), dsEntries, ttl);
  }

  private Mono<ResponseEntity<TransportMappingResponse>> handleError(Throwable error) {
    log.error("Failed to consolidate transport mappings: {}", error.getMessage());
    return ErrorResponseUtil.internalServerError(error);
  }
}
