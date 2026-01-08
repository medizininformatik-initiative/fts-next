package care.smith.fts.tca.rest;

import static care.smith.fts.tca.rest.FhirParameterExtractor.addParameter;
import static care.smith.fts.tca.rest.FhirParameterExtractor.addPart;
import static care.smith.fts.tca.rest.FhirParameterExtractor.extractRequiredString;
import static care.smith.fts.tca.rest.FhirParameterExtractor.extractRequiredStrings;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateIdentifier;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateIdentifiers;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;

import care.smith.fts.tca.rest.VfpsPseudonymizeResponse.PseudonymEntry;
import care.smith.fts.tca.services.TransportIdService;
import care.smith.fts.util.error.ErrorResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
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
import reactor.core.publisher.Mono;

/**
 * REST controller providing Vfps-compatible FHIR endpoint for RDA's FHIR Pseudonymizer.
 *
 * <p>This controller exposes a Vfps-compatible {@code $create-pseudonym} endpoint that resolves
 * transport IDs (tIDs) to their corresponding secure pseudonyms (sIDs):
 *
 * <ol>
 *   <li>Receives resolution requests from RDA's FHIR Pseudonymizer
 *   <li>Looks up tID→sID mappings in Redis (stored by CDA requests)
 *   <li>Returns real pseudonyms (sIDs) to the FHIR Pseudonymizer
 * </ol>
 *
 * <p>The RDA endpoint returns actual pseudonyms (unlike CDA endpoint which returns transport IDs),
 * completing the data isolation architecture where clinical data flows CDA→RDA but identifiers are
 * resolved via TCA.
 */
@Slf4j
@RestController
@RequestMapping(value = "api/v2")
@Validated
public class RdAgentFhirPseudonymizerController {

  private final TransportIdService transportIdService;

  public RdAgentFhirPseudonymizerController(TransportIdService transportIdService) {
    this.transportIdService = transportIdService;
  }

  /**
   * Vfps-compatible endpoint to resolve transport IDs to secure pseudonyms.
   *
   * <p>This endpoint accepts transport IDs (returned by CDA endpoint) and resolves them to their
   * corresponding secure pseudonyms (sIDs) stored in Redis.
   *
   * @param requestParams FHIR Parameters with namespace and originalValue(s) containing transport
   *     IDs
   * @return FHIR Parameters with namespace, originalValue, and pseudonymValue (real sID)
   */
  @PostMapping(
      value = "rd/fhir/$create-pseudonym",
      consumes = APPLICATION_FHIR_JSON_VALUE,
      produces = APPLICATION_FHIR_JSON_VALUE)
  @Operation(
      summary = "Resolve transport IDs to secure pseudonyms (Vfps-compatible)",
      description =
          "Accepts Vfps-format FHIR Parameters with transport IDs, "
              + "returns the corresponding secure pseudonyms (sIDs) from Redis.\n\n"
              + "This endpoint is used by RDA's FHIR Pseudonymizer to resolve transport IDs "
              + "received from CDA bundles to their final pseudonyms.",
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
                                      {"name": "originalValue", "valueString": "tID-abc123xyz..."}
                                    ]
                                  }
                                  """))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Secure pseudonyms resolved successfully",
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
                                    {"name": "originalValue", "valueString": "tID-abc123xyz..."},
                                    {"name": "pseudonymValue", "valueString": "sID-real-pseudonym"}
                                  ]
                                }
                                """))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (missing namespace or originalValue)",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Transport ID not found (may have expired)",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE))
      })
  public Mono<ResponseEntity<Parameters>> resolvePseudonyms(
      @Valid @RequestBody Parameters requestParams) {

    log.debug("Received Vfps $create-pseudonym request from RDA");

    return Mono.fromCallable(() -> parseRequest(requestParams))
        .flatMap(this::resolveTransportIds)
        .map(this::buildResponse)
        .map(ResponseEntity::ok)
        .onErrorResume(this::handleError);
  }

  private record ResolutionRequest(String namespace, List<String> transportIds) {}

  private ResolutionRequest parseRequest(Parameters params) {
    String namespace = validateIdentifier(extractRequiredString(params, "namespace"), "namespace");
    List<String> transportIds =
        validateIdentifiers(extractRequiredStrings(params, "originalValue"), "originalValue");

    log.debug(
        "Parsed RDA request: namespace={}, transportIdCount={}", namespace, transportIds.size());

    return new ResolutionRequest(namespace, transportIds);
  }

  private Mono<VfpsPseudonymizeResponse> resolveTransportIds(ResolutionRequest request) {
    var namespace = request.namespace();
    var transportIds = new HashSet<>(request.transportIds());

    log.debug("Resolving {} transport IDs for namespace={}", transportIds.size(), namespace);

    return transportIdService
        .fetchMappings(transportIds)
        .map(
            resolvedMappings -> {
              var entries =
                  request.transportIds().stream()
                      .map(
                          transportId -> {
                            var sId = resolvedMappings.get(transportId);
                            if (sId == null) {
                              log.warn("Transport ID not found: tId={}", transportId);
                              sId = transportId;
                            }
                            return new PseudonymEntry(namespace, transportId, sId);
                          })
                      .toList();
              return new VfpsPseudonymizeResponse(entries);
            })
        .doOnSuccess(
            response -> log.debug("Resolved {} transport IDs", response.pseudonyms().size()));
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
    log.warn("Error processing RDA $create-pseudonym request: {}", error.getMessage());

    if (error instanceof IllegalArgumentException) {
      return Mono.just(
          ResponseEntity.badRequest()
              .body(buildOperationOutcome(error.getMessage(), IssueType.INVALID)));
    }

    return ErrorResponseUtil.internalServerError(error);
  }

  private Parameters buildOperationOutcome(String message, IssueType issueType) {
    var outcome = new OperationOutcome();
    outcome.addIssue().setSeverity(IssueSeverity.ERROR).setCode(issueType).setDiagnostics(message);

    var params = new Parameters();
    params.addParameter().setName("outcome").setResource(outcome);
    return params;
  }
}
