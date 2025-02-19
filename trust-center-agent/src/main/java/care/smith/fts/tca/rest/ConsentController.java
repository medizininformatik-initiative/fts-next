package care.smith.fts.tca.rest;

import care.smith.fts.tca.consent.ConsentedPatientsProvider;
import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.util.MediaTypes;
import care.smith.fts.util.error.ErrorResponseUtil;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import care.smith.fts.util.tca.ConsentFetchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping(value = "api/v2")
@Validated
public class ConsentController {
  private final ConsentedPatientsProvider consentedPatientsProvider;
  private final int defaultPageSize;

  @Autowired
  ConsentController(
      ConsentedPatientsProvider consentedPatientsProvider,
      @Qualifier("defaultPageSize") int defaultPageSize) {
    this.consentedPatientsProvider = consentedPatientsProvider;
    this.defaultPageSize = defaultPageSize;
  }

  @PostMapping(
      value = "/cd/consented-patients/fetch-all",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaTypes.APPLICATION_FHIR_JSON_VALUE)
  @Operation(
      summary = "List of all consented patients",
      description = "**Since 5.0**\n\n",
      parameters = {
        @Parameter(
            name = "from",
            required = false,
            schema = @Schema(implementation = Integer.class, minimum = "0"),
            description = "Paging parameter"),
        @Parameter(
            name = "count",
            required = false,
            schema = @Schema(implementation = Integer.class, minimum = "1"),
            description = "Paging parameter")
      },
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = ConsentFetchAllRequest.class))),
      responses = {
        @ApiResponse(responseCode = "200", description = "Returns Bundle with consented patients"),
        @ApiResponse(responseCode = "400", description = "Unknown domain"),
      })
  public Mono<ResponseEntity<Bundle>> fetchAll(
      @RequestBody @Valid Mono<ConsentFetchAllRequest> request,
      UriComponentsBuilder uriBuilder,
      @RequestParam("from") Optional<Integer> from,
      @RequestParam("count") Optional<Integer> count) {
    var pagingParams = new PagingParams(from.orElse(0), count.orElse(defaultPageSize));
    var response =
        request.flatMap(r -> consentedPatientsProvider.fetchAll(r, uriBuilder, pagingParams));
    return response.map(ResponseEntity::ok).onErrorResume(ConsentController::errorResponse);
  }

  @PostMapping(
      value = "/cd/consented-patients/fetch",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaTypes.APPLICATION_FHIR_JSON_VALUE)
  @Operation(
      summary = "List of consented patients",
      description = "**Since 5.0**\n\n",
      parameters = {
        @Parameter(
            name = "from",
            required = false,
            schema = @Schema(implementation = Integer.class, minimum = "0"),
            description = "Paging parameter"),
        @Parameter(
            name = "count",
            required = false,
            schema = @Schema(implementation = Integer.class, minimum = "1"),
            description = "Paging parameter")
      },
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = ConsentFetchRequest.class))),
      responses = {
        @ApiResponse(responseCode = "200", description = "Returns Bundle with consented patients"),
        @ApiResponse(responseCode = "400", description = "Unknown domain"),
      })
  public Mono<ResponseEntity<Bundle>> fetch(
      @RequestBody @Valid Mono<ConsentFetchRequest> request,
      UriComponentsBuilder uriBuilder,
      @RequestParam("from") Optional<Integer> from,
      @RequestParam("count") Optional<Integer> count) {
    var pagingParams = new PagingParams(from.orElse(0), count.orElse(defaultPageSize));
    var response =
        request.flatMap(r -> consentedPatientsProvider.fetch(r, uriBuilder, pagingParams));
    return response.map(ResponseEntity::ok).onErrorResume(ConsentController::errorResponse);
  }

  private static Mono<ResponseEntity<Bundle>> errorResponse(Throwable e) {
    if (e instanceof UnknownDomainException) {
      return ErrorResponseUtil.badRequest(e);
    } else {
      return ErrorResponseUtil.internalServerError(e);
    }
  }
}
