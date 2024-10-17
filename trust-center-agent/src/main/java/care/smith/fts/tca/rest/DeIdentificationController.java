package care.smith.fts.tca.rest;

import care.smith.fts.tca.deidentification.MappingProvider;
import care.smith.fts.util.error.ErrorResponseUtil;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(value = "api/v2")
@Validated
public class DeIdentificationController {
  private final MappingProvider mappingProvider;

  @Autowired
  public DeIdentificationController(MappingProvider mappingProvider) {
    this.mappingProvider = mappingProvider;
  }

  @PostMapping(
      value = "/cd/transport-mapping",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ExceptionHandler(UnknownDomainException.class)
  public Mono<ResponseEntity<TransportMappingResponse>> transportMapping(
      @Valid @RequestBody Mono<TransportMappingRequest> requestData) {
    return requestData
        .flatMap(mappingProvider::generateTransportMapping)
        .map(ResponseEntity::ok)
        .onErrorResume(DeIdentificationController::handleGenerateError);
  }

  private static Mono<ResponseEntity<TransportMappingResponse>> handleGenerateError(Throwable e) {
    if (e instanceof UnknownDomainException || e instanceof IllegalArgumentException) {
      return ErrorResponseUtil.badRequest(e);
    } else {
      log.error("Internal error", e);
      return ErrorResponseUtil.internalServerError(e);
    }
  }

  @PostMapping(
      value = "/rd/research-mapping",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<ResearchMappingResponse>> researchMapping(
      @RequestBody @NotNull @Pattern(regexp = "^[\\w-]+$") String transferId) {
    log.trace("Resolve pseudonyms of map: {} ", transferId);
    return mappingProvider
        .fetchResearchMapping(transferId)
        .map(ResponseEntity::ok)
        .onErrorResume(e -> handleFetchError(transferId, e));
  }

  private static Mono<ResponseEntity<ResearchMappingResponse>> handleFetchError(
      String transferId, Throwable e) {
    log.error("Could not fetch pseudonyms of map {}: {}", transferId, e.getMessage());
    return ErrorResponseUtil.internalServerError(e);
  }
}
