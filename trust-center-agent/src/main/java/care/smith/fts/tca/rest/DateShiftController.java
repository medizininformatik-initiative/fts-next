package care.smith.fts.tca.rest;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static java.util.Set.of;

import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.services.TransportIdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller for date shift operations used by FHIR Pseudonymizer integration.
 *
 * <p>This controller provides endpoints for CDA and RDA to obtain deterministic date shift values
 * that can be passed to FHIR Pseudonymizer's {@code dateShiftFixedOffsetInDays} parameter.
 *
 * <p>The date shift is split between CDA and RDA for security: neither agent knows the total shift.
 *
 * <p>The flow is:
 *
 * <ol>
 *   <li>CDA calls /cd/dateshift with patientId → TCA stores temp: patientId → rdDateShift
 *   <li>CDA calls /cd/fhir/$create-pseudonym → TCA links temp to transportId
 *   <li>RDA calls /rd/dateshift with transportId → gets rdDateShift
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("api/v2")
@Validated
public class DateShiftController {

  private final TransportIdService transportIdService;
  private final GpasClient gpasClient;

  public DateShiftController(TransportIdService transportIdService, GpasClient gpasClient) {
    this.transportIdService = transportIdService;
    this.gpasClient = gpasClient;
  }

  /**
   * Generates date shift values for CDA's FHIR Pseudonymizer.
   *
   * <p>This endpoint:
   *
   * <ol>
   *   <li>Fetches a deterministic seed from gPAS based on patientId and maxDateShift
   *   <li>Generates cdDateShift and rdDateShift using the seed
   *   <li>Stores rdDateShift temporarily keyed by patientId (linked to transportId later)
   *   <li>Returns cdDateShift to CDA (converted to days)
   * </ol>
   *
   * @param request contains patientId, maxDateShift, preserve mode, and gPAS domain
   * @return CdDateShiftResponse with cdDateShiftDays
   */
  @PostMapping("cd/dateshift")
  @Operation(
      summary = "Generate date shift for CDA",
      description =
          "Generates deterministic date shifts for a patient. "
              + "Returns CDA's portion, stores RDA's portion temporarily in Redis.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Date shift generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "502", description = "gPAS service unavailable")
      })
  public Mono<ResponseEntity<CdDateShiftResponse>> generateCdDateShift(
      @Valid @RequestBody DateShiftRequest request) {

    log.debug(
        "Generating date shift for patient={}, maxDateShift={}, preserve={}",
        request.patientId(),
        request.maxDateShift(),
        request.dateShiftPreserve());

    return fetchDateShiftSeed(request)
        .flatMap(seed -> generateAndStoreDateShifts(seed, request))
        .map(ResponseEntity::ok)
        .doOnError(e -> log.error("Failed to generate date shift: {}", e.getMessage()));
  }

  private Mono<String> fetchDateShiftSeed(DateShiftRequest request) {
    var dateShiftKey = "%s_%s".formatted(request.maxDateShift().toString(), request.patientId());
    return gpasClient
        .fetchOrCreatePseudonyms(request.dateShiftDomain(), of(dateShiftKey))
        .map(m -> m.get(dateShiftKey));
  }

  private Mono<CdDateShiftResponse> generateAndStoreDateShifts(
      String seed, DateShiftRequest request) {
    var dateShifts = generate(seed, request.maxDateShift(), request.dateShiftPreserve());
    int cdDateShiftDays = (int) dateShifts.cdDateShift().toDays();
    int rdDateShiftDays = (int) dateShifts.rdDateShift().toDays();

    log.debug(
        "Generated date shifts: cdDays={}, rdDays={}, patientId={}",
        cdDateShiftDays,
        rdDateShiftDays,
        request.patientId());

    return transportIdService
        .storeTempDateShift(request.patientId(), rdDateShiftDays)
        .thenReturn(new CdDateShiftResponse(cdDateShiftDays));
  }

  /**
   * Retrieves the stored RDA date shift.
   *
   * <p>This endpoint looks up the rdDateShift linked to the transportId during pseudonymization.
   *
   * @param transportId the transport ID from the pseudonymized bundle
   * @return DateShiftResponse with rdDateShiftDays
   */
  @GetMapping("rd/dateshift")
  @Operation(
      summary = "Retrieve date shift for RDA",
      description = "Retrieves the stored RDA date shift portion for a transport ID.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Date shift retrieved successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Transport ID not found (may have expired)")
      })
  public Mono<ResponseEntity<DateShiftResponse>> getRdDateShift(
      @RequestParam("transportId") @NotNull @Pattern(regexp = "^[\\w-]+$") String transportId) {

    log.debug("Retrieving RDA date shift for transportId={}", transportId);

    return transportIdService
        .fetchDateShift(transportId)
        .map(rdDateShiftDays -> new DateShiftResponse(transportId, rdDateShiftDays))
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build())
        .doOnSuccess(
            resp -> {
              if (resp.getStatusCode().is2xxSuccessful()) {
                log.debug(
                    "Retrieved RDA date shift: transportId={}, days={}",
                    transportId,
                    resp.getBody().dateShiftDays());
              } else {
                log.warn("Date shift not found for transportId={}", transportId);
              }
            });
  }
}
