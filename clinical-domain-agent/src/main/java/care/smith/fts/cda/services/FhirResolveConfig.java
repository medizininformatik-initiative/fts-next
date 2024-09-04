package care.smith.fts.cda.services;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;

public record FhirResolveConfig(
    /* The system of the patient's identifier that is used to resolve a PID */
    @NotBlank String patientIdentifierSystem) {

  public FhirResolveConfig(@NotBlank String patientIdentifierSystem) {
    this.patientIdentifierSystem =
        requireNonNull(
            emptyToNull(patientIdentifierSystem),
            "Patient identifier system must not be null or empty");
  }

  public FhirResolveService createService(
      WebClient client, @Autowired MeterRegistry meterRegistry) {
    return new FhirResolveService(patientIdentifierSystem(), requireNonNull(client), meterRegistry);
  }
}
