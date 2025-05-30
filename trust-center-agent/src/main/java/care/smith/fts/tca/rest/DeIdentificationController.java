package care.smith.fts.tca.rest;

import care.smith.fts.tca.deidentification.MappingProvider;
import care.smith.fts.util.error.ErrorResponseUtil;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
  @Operation(
      summary = "Get the transport mapping",
      description = "**Since 5.0**\n\n",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = TransportMappingRequest.class))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = TransportMappingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad request"),
      })
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
      value = "/rd/secure-mapping",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get the secure mapping",
      description = "**Since 5.0**\n\n",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = String.class))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = SecureMappingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid transfer id"),
      })
  public Mono<ResponseEntity<SecureMappingResponse>> secureMapping(
      @RequestBody @NotNull @Pattern(regexp = "^[\\w-]+$") String transferId) {
    log.trace("Resolve pseudonyms of map: {} ", transferId);
    return mappingProvider
        .fetchSecureMapping(transferId)
        .map(ResponseEntity::ok)
        .onErrorResume(e -> handleFetchError(transferId, e));
  }

  private static Mono<ResponseEntity<SecureMappingResponse>> handleFetchError(
      String transferId, Throwable e) {
    log.error("Could not fetch pseudonyms of map {}: {}", transferId, e.getMessage());
    return ErrorResponseUtil.internalServerError(e);
  }
}
