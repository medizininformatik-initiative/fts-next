package care.smith.fts.tca.rest;

import static care.smith.fts.tca.rest.FhirParameterExtractor.addParameter;
import static care.smith.fts.tca.rest.FhirParameterExtractor.addPart;
import static care.smith.fts.tca.rest.FhirParameterExtractor.extractRequiredString;
import static care.smith.fts.tca.rest.FhirParameterExtractor.extractRequiredStrings;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateIdentifier;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateIdentifiers;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;

import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.rest.VfpsPseudonymizeResponse.PseudonymEntry;
import care.smith.fts.tca.services.TransportIdService;
import care.smith.fts.util.error.ErrorResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller providing Vfps-compatible FHIR endpoint for CDA's FHIR Pseudonymizer.
 *
 * <p>This controller exposes a Vfps-compatible {@code $create-pseudonym} endpoint that:
 *
 * <ol>
 *   <li>Receives pseudonymization requests from CDA's FHIR Pseudonymizer
 *   <li>Fetches real pseudonyms (sIDs) from gPAS
 *   <li>Generates transport IDs (tIDs) as temporary replacements
 *   <li>Stores tID→sID mappings in Redis for later resolution by RDA
 *   <li>Returns transport IDs (NOT real pseudonyms) to the FHIR Pseudonymizer
 * </ol>
 *
 * <p>This maintains data isolation: clinical data never reaches TCA, only identifiers flow through.
 */
@Slf4j
@RestController
@RequestMapping(value = "api/v2")
@Validated
public class CdAgentFhirPseudonymizerController {

  private final TransportIdService transportIdService;
  private final GpasClient gpasClient;

  public CdAgentFhirPseudonymizerController(
      TransportIdService transportIdService, GpasClient gpasClient) {
    this.transportIdService = transportIdService;
    this.gpasClient = gpasClient;
  }

  /**
   * Vfps-compatible endpoint to create pseudonyms (returns transport IDs).
   *
   * <p>This endpoint mimics Vfps's $create-pseudonym operation but returns transport IDs instead of
   * real pseudonyms, maintaining data isolation between domains.
   *
   * @param requestParams FHIR Parameters with namespace and originalValue(s)
   * @return FHIR Parameters with namespace, originalValue, and pseudonymValue (transport ID)
   */
  @PostMapping(
      value = "cd/fhir/$create-pseudonym",
      consumes = APPLICATION_FHIR_JSON_VALUE,
      produces = APPLICATION_FHIR_JSON_VALUE)
  @Operation(
      summary = "Create pseudonyms (Vfps-compatible, returns transport IDs)",
      description =
          "Accepts Vfps-format FHIR Parameters with namespace and original values, "
              + "returns transport IDs (NOT real pseudonyms) for data isolation.\n\n"
              + "The transport IDs can be resolved to real pseudonyms via the RDA endpoint.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = APPLICATION_FHIR_JSON_VALUE,
                      schema = @Schema(implementation = Parameters.class),
                      examples =
                          @ExampleObject(
                              value =
                                  """
                                  {
                                    "resourceType": "Parameters",
                                    "parameter": [
                                      {"name": "namespace", "valueString": "clinical-domain"},
                                      {"name": "originalValue", "valueString": "patient-123"}
                                    ]
                                  }
                                  """))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Transport IDs generated successfully",
            content =
                @Content(
                    mediaType = APPLICATION_FHIR_JSON_VALUE,
                    schema = @Schema(implementation = Parameters.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                                {
                                  "resourceType": "Parameters",
                                  "parameter": [
                                    {"name": "namespace", "valueString": "clinical-domain"},
                                    {"name": "originalValue", "valueString": "patient-123"},
                                    {"name": "pseudonymValue", "valueString": "tID-abc123xyz..."}
                                  ]
                                }
                                """))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (missing namespace or originalValue)",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE)),
        @ApiResponse(
            responseCode = "502",
            description = "Backend service unavailable",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE))
      })
  public Mono<ResponseEntity<Parameters>> createPseudonym(
      @Valid @RequestBody Parameters requestParams) {

    log.debug("Received Vfps $create-pseudonym request from CDA");

    return Mono.fromCallable(() -> parseRequest(requestParams))
        .flatMap(this::processRequest)
        .map(this::buildResponse)
        .map(ResponseEntity::ok)
        .onErrorResume(this::handleError);
  }

  private VfpsPseudonymizeRequest parseRequest(Parameters params) {
    String namespace = validateIdentifier(extractRequiredString(params, "namespace"), "namespace");
    List<String> originals =
        validateIdentifiers(extractRequiredStrings(params, "originalValue"), "originalValue");

    log.debug("Parsed request: namespace={}, originalCount={}", namespace, originals.size());

    return new VfpsPseudonymizeRequest(namespace, originals);
  }

  private Mono<VfpsPseudonymizeResponse> processRequest(VfpsPseudonymizeRequest request) {
    var namespace = request.namespace();
    var ttl = transportIdService.getDefaultTtl();

    log.debug("Processing {} identifiers for namespace={}", request.originals().size(), namespace);

    return gpasClient
        .fetchOrCreatePseudonyms(namespace, new HashSet<>(request.originals()))
        .flatMap(sIdMap -> generateTransportIds(sIdMap, namespace, ttl))
        .doOnSuccess(
            response -> log.debug("Generated {} transport IDs", response.pseudonyms().size()));
  }

  private Mono<VfpsPseudonymizeResponse> generateTransportIds(
      Map<String, String> sIdMap, String namespace, Duration ttl) {
    return Flux.fromIterable(sIdMap.entrySet())
        .flatMap(entry -> createPseudonymEntry(entry.getKey(), entry.getValue(), namespace, ttl))
        .collectList()
        .map(VfpsPseudonymizeResponse::new);
  }

  private Mono<PseudonymEntry> createPseudonymEntry(
      String original, String sId, String namespace, Duration ttl) {
    var tId = transportIdService.generateId();

    var linkDateShift =
        transportIdService
            .fetchAndDeleteTempDateShift(original)
            .flatMap(
                rdDateShift -> {
                  log.debug("Linking dateshift to tId={}, days={}", tId, rdDateShift);
                  return transportIdService.storeDateShift(tId, rdDateShift, ttl);
                })
            .onErrorResume(
                e -> {
                  log.warn(
                      "Failed to link dateshift for original={}: {}", original, e.getMessage());
                  return Mono.empty();
                });

    return Mono.when(transportIdService.storeMapping(tId, sId, ttl), linkDateShift)
        .thenReturn(new PseudonymEntry(namespace, original, tId));
  }

  private Parameters buildResponse(VfpsPseudonymizeResponse response) {
    var fhirParams = new Parameters();

    for (var entry : response.pseudonyms()) {
      // For single-value responses, use flat structure
      if (response.pseudonyms().size() == 1) {
        addParameter(fhirParams, "namespace", entry.namespace());
        addParameter(fhirParams, "originalValue", entry.original());
        addParameter(fhirParams, "pseudonymValue", entry.pseudonym());
      } else {
        // For batch responses, use nested structure
        var pseudonymParam = new ParametersParameterComponent();
        pseudonymParam.setName("pseudonym");

        addPart(pseudonymParam, "namespace", entry.namespace());
        addPart(pseudonymParam, "originalValue", entry.original());
        addPart(pseudonymParam, "pseudonymValue", entry.pseudonym());

        fhirParams.addParameter(pseudonymParam);
      }
    }

    log.trace("Built FHIR Parameters response with {} entries", response.pseudonyms().size());
    return fhirParams;
  }

  private Mono<ResponseEntity<Parameters>> handleError(Throwable error) {
    log.warn("Error processing $create-pseudonym request: {}", error.getMessage());

    if (error instanceof IllegalArgumentException) {
      return Mono.just(
          ResponseEntity.badRequest()
              .body(buildOperationOutcome(error.getMessage(), IssueType.INVALID)));
    }

    return ErrorResponseUtil.internalServerError(error);
  }

  private Parameters buildOperationOutcome(String message, IssueType issueType) {
    // Return error as FHIR OperationOutcome wrapped in Parameters for protocol compatibility
    var outcome = new OperationOutcome();
    outcome.addIssue().setSeverity(IssueSeverity.ERROR).setCode(issueType).setDiagnostics(message);

    var params = new Parameters();
    params.addParameter().setName("outcome").setResource(outcome);
    return params;
  }
}
