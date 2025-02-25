package care.smith.fts.util;

import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public interface FhirClientUtils {

  static Mono<CapabilityStatement> fetchCapabilityStatement(WebClient client) {
    return client.get().uri("/metadata").retrieve().bodyToMono(CapabilityStatement.class);
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

    var remainingOperations = new HashSet<>(operationNames);
    return capabilityStatement.getRest().stream()
        .flatMap(rest -> rest.getResource().stream())
        .flatMap(resource -> resource.getOperation().stream())
        .map(CapabilityStatementRestResourceOperationComponent::getName)
        .anyMatch(
            name -> {
              remainingOperations.remove(name);
              return remainingOperations.isEmpty();
            });
  }
}
