package care.smith.fts.tca.rest;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;
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
@RequestMapping("/api/v2/rd-agent/fhir")
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
      value = "/$create-pseudonym",
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

  private record ResolutionRequest(
      String namespace, List<String> transportIds, String transferId) {}

  private ResolutionRequest parseRequest(Parameters params) {
    // Extract namespace
    String namespace =
        params.getParameter().stream()
            .filter(p -> "namespace".equals(p.getName()))
            .findFirst()
            .map(ParametersParameterComponent::getValue)
            .map(Base::primitiveValue)
            .orElseThrow(
                () -> new IllegalArgumentException("Missing required parameter 'namespace'"));

    if (namespace.isBlank()) {
      throw new IllegalArgumentException("Parameter 'namespace' must not be empty");
    }

    // Extract transport IDs (originalValue parameters contain tIDs to resolve)
    List<String> transportIds =
        params.getParameter().stream()
            .filter(p -> "originalValue".equals(p.getName()))
            .map(ParametersParameterComponent::getValue)
            .map(Base::primitiveValue)
            .toList();

    if (transportIds.isEmpty()) {
      throw new IllegalArgumentException("At least one 'originalValue' parameter is required");
    }

    // Extract transferId if provided (for scoped lookups)
    String transferId =
        params.getParameter().stream()
            .filter(p -> "transferId".equals(p.getName()))
            .findFirst()
            .map(ParametersParameterComponent::getValue)
            .map(Base::primitiveValue)
            .orElse(null);

    log.debug(
        "Parsed RDA request: namespace={}, transportIdCount={}, transferId={}",
        namespace,
        transportIds.size(),
        transferId);

    return new ResolutionRequest(namespace, transportIds, transferId);
  }

  private Mono<VfpsPseudonymizeResponse> resolveTransportIds(ResolutionRequest request) {
    var namespace = request.namespace();
    var transportIds = new HashSet<>(request.transportIds());
    var transferId = request.transferId();

    log.debug(
        "Resolving {} transport IDs for namespace={}, transferId={}",
        transportIds.size(),
        namespace,
        transferId);

    if (transferId == null) {
      // Without transferId, we can't resolve (need to know which session the tIDs belong to)
      return Mono.error(
          new IllegalArgumentException("Parameter 'transferId' is required for RDA resolution"));
    }

    return transportIdService
        .resolveMappings(transferId, transportIds)
        .map(
            resolvedMappings -> {
              List<PseudonymEntry> entries = new ArrayList<>();
              for (var transportId : request.transportIds()) {
                var sId = resolvedMappings.get(transportId);
                if (sId != null) {
                  entries.add(new PseudonymEntry(namespace, transportId, sId));
                } else {
                  log.warn(
                      "Transport ID not found: tId={}, transferId={}", transportId, transferId);
                  // Return the tID as-is if not found (or could throw error)
                  entries.add(new PseudonymEntry(namespace, transportId, transportId));
                }
              }
              return new VfpsPseudonymizeResponse(entries);
            })
        .doOnSuccess(
            response ->
                log.debug(
                    "Resolved {} transport IDs for transferId={}",
                    response.pseudonyms().size(),
                    transferId));
  }

  private Parameters buildResponse(VfpsPseudonymizeResponse response) {
    var fhirParams = new Parameters();

    for (var entry : response.pseudonyms()) {
      // For single-value responses, use flat structure
      if (response.pseudonyms().size() == 1) {
        fhirParams.addParameter().setName("namespace").setValue(new StringType(entry.namespace()));
        fhirParams
            .addParameter()
            .setName("originalValue")
            .setValue(new StringType(entry.original()));
        fhirParams
            .addParameter()
            .setName("pseudonymValue")
            .setValue(new StringType(entry.pseudonym()));
      } else {
        // For batch responses, use nested structure
        var pseudonymParam = new ParametersParameterComponent();
        pseudonymParam.setName("pseudonym");

        pseudonymParam.addPart().setName("namespace").setValue(new StringType(entry.namespace()));
        pseudonymParam
            .addPart()
            .setName("originalValue")
            .setValue(new StringType(entry.original()));
        pseudonymParam
            .addPart()
            .setName("pseudonymValue")
            .setValue(new StringType(entry.pseudonym()));

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
