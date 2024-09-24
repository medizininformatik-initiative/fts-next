package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public record ConsentRequest(
    @NotNull(groups = ConsentRequest.class) String domain,
    @NotNull(groups = ConsentRequest.class) Set<String> policies,
    @NotBlank(groups = ConsentRequest.class) String policySystem,
    List<String> pids) {}
