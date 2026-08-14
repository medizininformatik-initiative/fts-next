package care.smith.fts.tca.rest;

import static care.smith.fts.tca.rest.FhirParameterExtractor.extractRequiredIdentifier;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateIdentifier;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateValue;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;

import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.services.TransportIdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller providing the {@code $pseudonymize} operation of the MII Pseudonymization IG
 * (2026.1.0) for CDA's FHIR Pseudonymizer.
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
      summary = "Pseudonymize an identifier (MII IG $pseudonymize, returns transport ID)",
      description =
          "Accepts FHIR Parameters as defined by the MII Pseudonymization IG with `context` and "
              + "`original` Identifiers, returns a transport ID (NOT the real pseudonym) as the "
              + "`pseudonym` Identifier for data isolation.\n\n"
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
                                      {"name": "context", "valueIdentifier":
                                        {"system": "http://fts.smith.care", "value": "domain"}},
                                      {"name": "original", "valueIdentifier":
                                        {"system": "http://fts.smith.care", "value": "patient-123"}}
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
                                    {"name": "pseudonym", "valueIdentifier":
                                      {"value": "tID-abc123xyz..."}}
                                  ]
                                }
                                """))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (missing context or original)",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE)),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected error during processing",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE))
      })
  public Mono<ResponseEntity<Resource>> pseudonymize(@Valid @RequestBody Parameters requestParams) {

    log.debug("Received MII $pseudonymize request from CDA");

    return Mono.fromCallable(() -> parseRequest(requestParams))
        .flatMap(this::processRequest)
        .map(ResponseEntity::<Resource>ok)
        .onErrorResume(this::handleError);
  }

  private record PseudonymizeRequest(Identifier context, Identifier original) {}

  private PseudonymizeRequest parseRequest(Parameters params) {
    var context = extractRequiredIdentifier(params, "context");
    var original = extractRequiredIdentifier(params, "original");
    validateIdentifier(context.getValue(), "context");
    validateValue(original.getValue(), "original");

    log.debug("Parsed request: context={}, original={}", context.getValue(), original.getValue());

    return new PseudonymizeRequest(context, original);
  }

  private Mono<Resource> processRequest(PseudonymizeRequest request) {
    var ttl = transportIdService.getDefaultTtl();
    var domain = request.context().getValue();
    var original = request.original().getValue();

    return gpasClient
        .fetchOrCreatePseudonyms(domain, Set.of(original))
        .flatMap(
            sIdMap -> {
              var sId = sIdMap.get(original);
              var tId = transportIdService.generateId();
              return transportIdService
                  .storeMapping(tId, sId, ttl)
                  .thenReturn(buildResponse(request, tId));
            });
  }

  private Resource buildResponse(PseudonymizeRequest request, String tId) {
    var fhirParams = new Parameters();
    fhirParams.addParameter().setName("context").setValue(request.context().copy());
    fhirParams.addParameter().setName("original").setValue(request.original().copy());
    fhirParams.addParameter().setName("pseudonym").setValue(new Identifier().setValue(tId));
    return fhirParams;
  }

  private Mono<ResponseEntity<Resource>> handleError(Throwable error) {
    log.warn("Error processing $pseudonymize request: {}", error.getMessage());

    if (error instanceof IllegalArgumentException) {
      return Mono.just(
          ResponseEntity.badRequest()
              .body(buildOperationOutcome(error.getMessage(), IssueType.INVALID)));
    }

    return Mono.just(
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                buildOperationOutcome("Unexpected error during processing", IssueType.EXCEPTION)));
  }

  private Resource buildOperationOutcome(String message, IssueType issueType) {
    var outcome = new OperationOutcome();
    outcome.addIssue().setSeverity(IssueSeverity.ERROR).setCode(issueType).setDiagnostics(message);
    return outcome;
  }
}
