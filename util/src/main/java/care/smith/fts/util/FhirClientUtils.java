package care.smith.fts.util;

import static java.util.stream.Collectors.toSet;

import care.smith.fts.util.error.fhir.FhirConnectException;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public interface FhirClientUtils {

  static Mono<CapabilityStatement> fetchCapabilityStatementOperations(WebClient client) {
    return client.get().uri("/metadata?_elements=rest.operation").retrieve().bodyToMono(CapabilityStatement.class);
  }

  /**
   * Verifies if all specified Resource operation names are present in the given
   * CapabilityStatement.
   *
   * @param capabilityStatement The FHIR CapabilityStatement to check
   * @param operationNames List of operation names to verify
   * @return boolean indicating if all operations were found (true) or not (false)
   */
  static boolean verifyOperationsExist(
      @NotNull CapabilityStatement capabilityStatement, @NotNull List<String> operationNames) {

    return capabilityStatement.getRest().stream()
        .flatMap(resource -> resource.getOperation().stream())
        .map(CapabilityStatementRestResourceOperationComponent::getName)
        .collect(toSet())
        .containsAll(operationNames);
  }

  /**
   * Verifies if all specified Resource operation names are present in the given
   * CapabilityStatement.
   *
   * @param capabilityStatement The FHIR CapabilityStatement to check
   * @param operationNames List of operation names to verify
   * @return An error mono if an operation is missing, a mono with the capability statement
   *     otherwise
   */
  static Mono<CapabilityStatement> requireOperations(
      CapabilityStatement capabilityStatement, List<String> operationNames) {
    return verifyOperationsExist(capabilityStatement, operationNames)
        ? Mono.just(capabilityStatement)
        : Mono.error(new FhirConnectException("Server is missing capabilities"));
  }
}
