package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record ConsentFetchAllRequest(
    @NotNull(groups = ConsentFetchAllRequest.class) String domain,
    @NotNull(groups = ConsentFetchAllRequest.class) Set<String> policies,
    @NotBlank(groups = ConsentFetchAllRequest.class) String policySystem)
    implements ConsentRequest {}
