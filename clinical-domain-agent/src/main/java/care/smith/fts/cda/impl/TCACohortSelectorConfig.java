package care.smith.fts.cda.impl;

import care.smith.fts.util.HTTPClientConfig;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonClassDescription("")
public record TCACohortSelectorConfig(
    /* */
    @NotNull HTTPClientConfig server,

    /* */
    List<String> policies,

    /* */
    String domain) {}
