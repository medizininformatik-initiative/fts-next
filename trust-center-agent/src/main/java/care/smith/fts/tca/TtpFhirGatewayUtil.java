package care.smith.fts.tca;

import static care.smith.fts.util.fhir.FhirClientUtils.fetchCapabilityStatementOperations;
import static care.smith.fts.util.fhir.FhirClientUtils.requireOperations;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static reactor.core.Exceptions.isRetryExhausted;

import care.smith.fts.util.error.fhir.FhirException;
import care.smith.fts.util.error.fhir.FhirUnknownDomainException;
import care.smith.fts.util.error.fhir.NoFhirServerException;
import java.util.List;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

public interface TtpFhirGatewayUtil {

  Logger LOGGER = LoggerFactory.getLogger(TtpFhirGatewayUtil.class);

  static <T> Mono<T> handleError(String toolName, Throwable e) {
    if (isRetryExhausted(e)) {
      if (e.getCause() instanceof WebClientRequestException) {
        return Mono.error(
            new NoFhirServerException("No connection to %s server".formatted(toolName), e));
      } else if (e.getCause() instanceof WebClientResponseException) {
        return Mono.error(
            new FhirException(INTERNAL_SERVER_ERROR, "Unknown %s error".formatted(toolName), e));
      }
    }
    return Mono.error(e);
  }

  static Mono<Throwable> handle4xxError(
      String toolName, WebClient client, List<String> operations, ClientResponse r) {
    LOGGER.trace("{} response headers: {}", toolName, r.headers().asHttpHeaders());
    LOGGER.debug("{} status code: {}", toolName, r.statusCode());

    return fetchCapabilityStatementOperations(client)
        .flatMap(capabilities -> requireOperations(capabilities, operations))
        .onErrorResume(e -> handleUnknownServerType(toolName, e))
        .flatMap(x -> handleKnownServerError(toolName, r));
  }

  /** Handle the server looking ok capability-wise, but having errors all the same */
  private static Mono<Throwable> handleKnownServerError(String toolName, ClientResponse r) {
    return r.bodyToMono(OperationOutcome.class)
        .onErrorResume(e -> handleParsingError(toolName))
        .flatMap(outcome -> handleFhirError(toolName, r, outcome));
  }

  /** Handle the server looking not ok capability-wise */
  private static Mono<CapabilityStatement> handleUnknownServerType(String toolName, Throwable e) {
    return Mono.error(new NoFhirServerException("This is no %s server".formatted(toolName), e));
  }

  /** Handle parsing error, when inspecting the operation outcome */
  private static Mono<OperationOutcome> handleParsingError(String toolName) {
    return Mono.error(
        new FhirException(
            INTERNAL_SERVER_ERROR,
            "Unexpected Error: Cannot parse OperationOutcome from %s".formatted(toolName)));
  }

  /** Handle an error from a probably correct server */
  private static Mono<Throwable> handleFhirError(
      String toolName, ClientResponse r, OperationOutcome operationOutcome) {
    var diagnostics = operationOutcome.getIssueFirstRep().getDiagnostics();
    LOGGER.error(
        "The server looked like {}, but answered with an operationOutcome: {}.",
        toolName,
        diagnostics);
    return Mono.error(determineException(toolName, r));
  }

  private static Throwable determineException(String toolName, ClientResponse r) {
    return switch (r.statusCode()) {
      case BAD_REQUEST ->
          new FhirException(
              INTERNAL_SERVER_ERROR, "Missing or faulty parameters. This should not happen");
      case UNAUTHORIZED ->
          new FhirException(
              SERVICE_UNAVAILABLE, "Invalid %s FHIR gateway configuration".formatted(toolName));
      case NOT_FOUND -> new FhirUnknownDomainException("%s domain not found".formatted(toolName));
      case UNPROCESSABLE_ENTITY ->
          new FhirException(UNPROCESSABLE_ENTITY, "Missing or incorrect patient attributes");
      default -> new FhirException(INTERNAL_SERVER_ERROR, "Unknown Error. This should not happen");
    };
  }
}
