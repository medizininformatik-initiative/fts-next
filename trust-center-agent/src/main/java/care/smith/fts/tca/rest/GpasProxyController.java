package care.smith.fts.tca.rest;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.services.TransportIdService;
import care.smith.fts.util.error.ErrorResponseUtil;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Identifier;
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
 * gPAS-compatible proxy that returns transport IDs instead of real pseudonyms.
 *
 * <p>The external FHIR Pseudonymizer calls this endpoint believing it's gPAS. TCA fetches real
 * pseudonyms from gPAS, stores tID→sID mappings in Redis, and returns transport IDs.
 */
@Slf4j
@RestController
@RequestMapping(value = "api/v2")
@Validated
public class GpasProxyController {

  private final TransportIdService transportIdService;
  private final GpasClient gpasClient;

  public GpasProxyController(TransportIdService transportIdService, GpasClient gpasClient) {
    this.transportIdService = transportIdService;
    this.gpasClient = gpasClient;
  }

  @PostMapping(
      value = "cd/fp-gpas-proxy/$pseudonymizeAllowCreate",
      consumes = APPLICATION_FHIR_JSON_VALUE,
      produces = APPLICATION_FHIR_JSON_VALUE)
  public Mono<ResponseEntity<Parameters>> pseudonymizeAllowCreate(
      @Valid @RequestBody Parameters requestParams) {

    log.debug("Received gPAS proxy $pseudonymizeAllowCreate request");

    return Mono.fromCallable(() -> parseGpasRequest(requestParams))
        .flatMap(this::processRequest)
        .map(this::buildGpasResponse)
        .map(ResponseEntity::ok)
        .onErrorResume(this::handleError);
  }

  private GpasProxyRequest parseGpasRequest(Parameters params) {
    String target = extractRequired(params, "target");
    List<String> originals = extractAll(params, "original");

    if (originals.isEmpty()) {
      throw new IllegalArgumentException("At least one 'original' parameter is required");
    }

    log.debug("Parsed gPAS proxy request: target={}, originalCount={}", target, originals.size());
    return new GpasProxyRequest(target, originals);
  }

  private Mono<Map<String, String>> processRequest(GpasProxyRequest request) {
    var ttl = transportIdService.getDefaultTtl();

    return gpasClient
        .fetchOrCreatePseudonyms(request.target(), new HashSet<>(request.originals()))
        .flatMap(sIdMap -> replaceWithTransportIds(sIdMap, ttl));
  }

  /** For each original→sID mapping, generate a tID, store tID→sID, return original→tID. */
  private Mono<Map<String, String>> replaceWithTransportIds(
      Map<String, String> sIdMap, Duration ttl) {
    return Flux.fromIterable(sIdMap.entrySet())
        .flatMap(
            entry -> {
              var tId = transportIdService.generateId();
              return transportIdService
                  .storeMapping(tId, entry.getValue(), ttl)
                  .thenReturn(Map.entry(entry.getKey(), tId));
            })
        .collectList()
        .map(entries -> entries.stream().collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  /** Builds gPAS-compatible $pseudonymizeAllowCreate response with valueIdentifier parts. */
  private Parameters buildGpasResponse(Map<String, String> originalToTid) {
    var fhirParams = new Parameters();

    for (var entry : originalToTid.entrySet()) {
      var pseudonymParam = new ParametersParameterComponent();
      pseudonymParam.setName("pseudonym");

      pseudonymParam
          .addPart()
          .setName("original")
          .setValue(new Identifier().setValue(entry.getKey()));
      pseudonymParam
          .addPart()
          .setName("pseudonym")
          .setValue(new Identifier().setValue(entry.getValue()));

      fhirParams.addParameter(pseudonymParam);
    }

    log.trace("Built gPAS proxy response with {} entries", originalToTid.size());
    return fhirParams;
  }

  private String extractRequired(Parameters params, String name) {
    return params.getParameter().stream()
        .filter(p -> name.equals(p.getName()))
        .findFirst()
        .map(ParametersParameterComponent::getValue)
        .map(Base::primitiveValue)
        .orElseThrow(
            () -> new IllegalArgumentException("Missing required parameter '%s'".formatted(name)));
  }

  private List<String> extractAll(Parameters params, String name) {
    return params.getParameter().stream()
        .filter(p -> name.equals(p.getName()))
        .map(ParametersParameterComponent::getValue)
        .map(Base::primitiveValue)
        .toList();
  }

  private Mono<ResponseEntity<Parameters>> handleError(Throwable error) {
    log.warn("Error processing gPAS proxy request: {}", error.getMessage());

    if (error instanceof IllegalArgumentException) {
      var outcome = new OperationOutcome();
      outcome
          .addIssue()
          .setSeverity(IssueSeverity.ERROR)
          .setCode(IssueType.INVALID)
          .setDiagnostics(error.getMessage());

      var params = new Parameters();
      params.addParameter().setName("outcome").setResource(outcome);
      return Mono.just(ResponseEntity.badRequest().body(params));
    }

    return ErrorResponseUtil.internalServerError(error);
  }

  private record GpasProxyRequest(String target, List<String> originals) {}
}
