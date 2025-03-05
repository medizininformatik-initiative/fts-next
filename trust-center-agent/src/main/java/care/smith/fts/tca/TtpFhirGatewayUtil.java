package care.smith.fts.tca;

import static care.smith.fts.util.FhirClientUtils.fetchCapabilityStatementOperations;
import static care.smith.fts.util.FhirClientUtils.requireOperations;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import care.smith.fts.util.error.fhir.FhirException;
import care.smith.fts.util.error.fhir.FhirUnknownDomainException;
import care.smith.fts.util.error.fhir.NoFhirServerException;
import java.util.List;
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

  static Mono<?> handleError(String name, Throwable e) {
    if (e.getCause() instanceof WebClientRequestException) {
      return Mono.error(new NoFhirServerException("No connection to " + name + " server"));
    } else if (e.getCause() instanceof WebClientResponseException) {
      return Mono.error(new FhirException(INTERNAL_SERVER_ERROR, name + " kapuut"));
    } else {
      return Mono.error(e);
    }
  }

  static Mono<Throwable> handle4xxError(
      String name, WebClient client, List<String> operations, ClientResponse r) {
    LOGGER.trace("{} response headers: {}", name, r.headers().asHttpHeaders());
    LOGGER.debug("{} status code: {}", name, r.statusCode().value());

    return fetchCapabilityStatementOperations(client)
        .flatMap(c1 -> requireOperations(c1, operations))
        .onErrorResume(e -> Mono.error(new NoFhirServerException("This is no " + name + " server")))
        .flatMap(
            x ->
                r.bodyToMono(OperationOutcome.class)
                    .onErrorResume(
                        e ->
                            Mono.error(
                                new FhirException(
                                    INTERNAL_SERVER_ERROR,
                                    "Unexpected Error: Cannot parse OperationOutcome from "
                                        + name)))
                    .flatMap(
                        operationOutcome -> {
                          var diagnostics = operationOutcome.getIssueFirstRep().getDiagnostics();
                          LOGGER.error(diagnostics);
                          return switch (r.statusCode()) {
                            case BAD_REQUEST ->
                                // fehlende oder falsche params
                                // kann eigentlich nicht passieren
                                Mono.error(
                                    new FhirException(
                                        BAD_REQUEST, "Fehlende oder fehlerhafte Parameter."));
                            case UNAUTHORIZED ->
                                Mono.error(
                                    new FhirException(
                                        INTERNAL_SERVER_ERROR,
                                        "Invalid " + name + " FHIR gateway configuration"));
                            case NOT_FOUND ->
                                // kommt, wenn die gics-Domain unbekannt ist
                                Mono.error(
                                    new FhirUnknownDomainException(
                                        "Parameter mit unbekanntem Inhalt"));
                            case UNPROCESSABLE_ENTITY ->
                                // keine Ahnung wann der kommt
                                Mono.error(
                                    new FhirException(
                                        UNPROCESSABLE_ENTITY,
                                        "Fehlende oder falsche Patienten-Attribute."));
                            default ->
                                Mono.error(
                                    new FhirException(INTERNAL_SERVER_ERROR, "Unknown Error^^"));
                          };
                        }));
  }
}
