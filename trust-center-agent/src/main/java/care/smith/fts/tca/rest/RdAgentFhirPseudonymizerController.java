package care.smith.fts.tca.rest;

import static care.smith.fts.tca.rest.FhirParameterExtractor.extractRequiredIdentifier;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateIdentifier;
import static care.smith.fts.tca.rest.FhirParameterExtractor.validateValue;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;

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
 * REST controller providing the {@code $de-pseudonymize} operation of the MII Pseudonymization IG
 * (2026.1.0) for RDA's FHIR Pseudonymizer.
 *
 * <p>This controller resolves transport IDs (tIDs) to their corresponding secure pseudonyms (sIDs):
 *
 * <ol>
 *   <li>Receives de-pseudonymization requests from RDA's FHIR Pseudonymizer
 *   <li>Looks up tID→sID mappings in Redis (stored by CDA requests)
 *   <li>Returns real pseudonyms (sIDs) to the FHIR Pseudonymizer
 * </ol>
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

  @PostMapping(
      value = "rd/fhir/$de-pseudonymize",
      consumes = APPLICATION_FHIR_JSON_VALUE,
      produces = APPLICATION_FHIR_JSON_VALUE)
  @Operation(
      summary = "De-pseudonymize a transport ID to its secure pseudonym (MII IG $de-pseudonymize)",
      description =
          "Accepts FHIR Parameters as defined by the MII Pseudonymization IG with `context` and "
              + "`pseudonym` Identifiers, returns the corresponding secure pseudonym (sID) as the "
              + "`original.value` Identifier.\n\n"
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
                                      {"name": "context", "valueIdentifier":
                                        {"system": "http://fts.smith.care", "value": "domain"}},
                                      {"name": "pseudonym", "valueIdentifier":
                                        {"system": "http://fts.smith.care",
                                         "value": "tID-abc123xyz..."}}
                                    ]
                                  }
                                  """))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Secure pseudonym resolved successfully",
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
                                    {
                                      "name": "original",
                                      "part": [
                                        {"name": "value", "valueIdentifier":
                                          {"value": "sID-real-pseudonym"}}
                                      ]
                                    }
                                  ]
                                }
                                """))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (missing context or pseudonym)",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Transport ID not found (may have expired)",
            content = @Content(mediaType = APPLICATION_FHIR_JSON_VALUE))
      })
  public Mono<ResponseEntity<Resource>> dePseudonymize(
      @Valid @RequestBody Parameters requestParams) {

    log.debug("Received MII $de-pseudonymize request from RDA");

    return Mono.fromCallable(() -> parseRequest(requestParams))
        .flatMap(this::resolveTransportId)
        .onErrorResume(this::handleError);
  }

  private record DePseudonymizeRequest(Identifier context, Identifier pseudonym) {}

  private DePseudonymizeRequest parseRequest(Parameters params) {
    var context = extractRequiredIdentifier(params, "context");
    var pseudonym = extractRequiredIdentifier(params, "pseudonym");
    validateIdentifier(context.getValue(), "context");
    validateValue(pseudonym.getValue(), "pseudonym");

    log.debug(
        "Parsed RDA request: context={}, pseudonym={}", context.getValue(), pseudonym.getValue());

    return new DePseudonymizeRequest(context, pseudonym);
  }

  private Mono<ResponseEntity<Resource>> resolveTransportId(DePseudonymizeRequest request) {
    var tId = request.pseudonym().getValue();
    return transportIdService
        .fetchMappings(Set.of(tId))
        .map(
            resolvedMappings -> {
              var sId = resolvedMappings.get(tId);
              if (sId == null) {
                log.warn("Transport ID not found: tId={}", tId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        buildOperationOutcome(
                            "Pseudonym '%s' not found".formatted(tId), IssueType.NOTFOUND));
              }
              return ResponseEntity.<Resource>ok(buildResponse(request, sId));
            });
  }

  private Resource buildResponse(DePseudonymizeRequest request, String sId) {
    var fhirParams = new Parameters();
    var originalParam = fhirParams.addParameter().setName("original");
    originalParam.addPart().setName("context").setValue(request.context().copy());
    originalParam.addPart().setName("value").setValue(new Identifier().setValue(sId));
    originalParam.addPart().setName("pseudonym").setValue(request.pseudonym().copy());
    return fhirParams;
  }

  private Mono<ResponseEntity<Resource>> handleError(Throwable error) {
    log.warn("Error processing $de-pseudonymize request: {}", error.getMessage());

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
