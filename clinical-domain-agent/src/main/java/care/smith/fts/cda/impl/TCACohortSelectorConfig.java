package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@JsonClassDescription("")
public record TCACohortSelectorConfig(
    /* */
    @NotNull HttpClientConfig server,
    /* */
    @NotNull String patientIdentifierSystem,
    /* */
    @NotNull String policySystem,

    /* */
    Set<String> policies,

    /* */
    String domain) {}
