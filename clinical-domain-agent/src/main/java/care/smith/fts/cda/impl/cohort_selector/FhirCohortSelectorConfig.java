package care.smith.fts.cda.impl.cohort_selector;

import care.smith.fts.util.HttpClientConfig;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@JsonClassDescription("Configuration for the FHIR server based cohort selector")
public record FhirCohortSelectorConfig(
    /* FHIR server connection configuration */
    @NotNull HttpClientConfig server,

    /* System used for patient identifiers */
    @NotNull String patientIdentifierSystem,

    /* System used for consent policy codes */
    @NotNull String policySystem,

    /* Set of policy codes to filter consents by */
    Set<String> policies,

    /* Optional domain/organization to filter consents by */
    String domain) {}
