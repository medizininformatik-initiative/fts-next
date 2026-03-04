package care.smith.fts.tca.rest;

import static care.smith.fts.tca.rest.FhirParameterExtractor.addParameter;
import static care.smith.fts.tca.rest.FhirParameterExtractor.extractRequiredString;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateIdentifier;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateValue;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;

import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.services.TransportIdService;
import care.smith.fts.util.error.ErrorResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller providing MII-compatible FHIR {@code $pseudonymize} endpoint for CDA's FHIR
 * Pseudonymizer.
 *
 * <p>This controller:
 *
 * <ol>
 *   <li>Receives pseudonymization requests from CDA's FHIR Pseudonymizer
 *   <li>Fetches real pseudonyms (sIDs) from gPAS
 *   <li>Generates transport IDs (tIDs) as temporary replacements
 *   <li>Stores tID→sID mappings in Redis for later resolution by RDA
 *   <li>Returns transport IDs (NOT real pseudonyms) to the FHIR Pseudonymizer
 * </ol>
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

  @PostMapping(
      value = "cd/fhir/$pseudonymize",
      consumes = APPLICATION_FHIR_JSON_VALUE,
      produces = APPLICATION_FHIR_JSON_VALUE)
  @Operation(
      summary = "Pseudonymize an identifier (MII protocol, returns transport ID)",
      description =
          "Accepts MII-format FHIR Parameters with target domain and original value, "
              + "returns a transport ID (NOT the real pseudonym) for data isolation.\n\n"
              + "The transport ID can be resolved to the real pseudonym via the RDA "
              + "$de-pseudonymize endpoint.",
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
                                      {"name": "target", "valueString": "clinical-domain"},
                                      {"name": "original", "valueString": "patient-123"},
                                      {"name": "allowCreate", "valueBoolean": true}
                                    ]
                                  }
                                  """))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Transport ID generated successfully",
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
                                    {"name": "pseudonym", "valueString": "tID-abc123xyz..."}
                                  ]
                                }
                                """))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (missing target or original)",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE)),
        @ApiResponse(
            responseCode = "502",
            description = "Backend service unavailable",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE))
      })
  public Mono<ResponseEntity<Parameters>> pseudonymize(
      @Valid @RequestBody Parameters requestParams) {

    log.debug("Received MII $pseudonymize request from CDA");

    return Mono.fromCallable(() -> parseRequest(requestParams))
        .flatMap(this::processRequest)
        .map(ResponseEntity::ok)
        .onErrorResume(this::handleError);
  }

  private record PseudonymizeRequest(String target, String original) {}

  private PseudonymizeRequest parseRequest(Parameters params) {
    String target = validateIdentifier(extractRequiredString(params, "target"), "target");
    String original = validateValue(extractRequiredString(params, "original"), "original");

    log.debug("Parsed request: target={}, original={}", target, original);

    return new PseudonymizeRequest(target, original);
  }

  private Mono<Parameters> processRequest(PseudonymizeRequest request) {
    var ttl = transportIdService.getDefaultTtl();

    return gpasClient
        .fetchOrCreatePseudonyms(request.target(), Set.of(request.original()))
        .flatMap(
            sIdMap -> {
              var sId = sIdMap.get(request.original());
              var tId = transportIdService.generateId();
              return transportIdService.storeMapping(tId, sId, ttl).thenReturn(buildResponse(tId));
            });
  }

  private Parameters buildResponse(String tId) {
    var fhirParams = new Parameters();
    addParameter(fhirParams, "pseudonym", tId);
    return fhirParams;
  }

  private Mono<ResponseEntity<Parameters>> handleError(Throwable error) {
    log.warn("Error processing $pseudonymize request: {}", error.getMessage());

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
