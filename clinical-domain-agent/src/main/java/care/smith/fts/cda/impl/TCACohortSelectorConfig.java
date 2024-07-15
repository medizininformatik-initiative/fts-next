package care.smith.fts.cda.impl;

import care.smith.fts.util.HTTPClientConfig;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@JsonClassDescription("")
public record TCACohortSelectorConfig(
    /* */
    @NotNull HTTPClientConfig server,
    /* */
    @NotNull String patientIdentifierSystem,
    /* */
    @NotNull String policySystem,

    /* */
    Set<String> policies,

    /* */
    String domain) {}
