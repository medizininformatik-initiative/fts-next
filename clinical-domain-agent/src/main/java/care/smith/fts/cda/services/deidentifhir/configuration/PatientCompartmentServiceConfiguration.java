package care.smith.fts.cda.services.deidentifhir.configuration;

import care.smith.fts.cda.services.deidentifhir.PatientCompartmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class PatientCompartmentServiceConfiguration {

  private static final String DEFAULT_COMPARTMENT_DEFINITION_PATH =
      "fhir/compartmentdefinition-patient.json";

  /** Override this method in tests to use a different resource path. */
  protected String getCompartmentDefinitionPath() {
    return DEFAULT_COMPARTMENT_DEFINITION_PATH;
  }

  @Bean
  public Map<String, List<String>> patientCompartmentParams(ObjectMapper objectMapper) {
    var resourceTypeToParams =
        PatientCompartmentService.loadCompartmentDefinition(
            objectMapper, getCompartmentDefinitionPath());
    log.info(
        "Loaded patient compartment params with {} resource types having compartment params",
        resourceTypeToParams.values().stream().filter(params -> !params.isEmpty()).count());
    return resourceTypeToParams;
  }
}
