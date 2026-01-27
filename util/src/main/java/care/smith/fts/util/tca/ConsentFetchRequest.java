package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public record ConsentFetchRequest(
    @NotNull(groups = ConsentFetchRequest.class) String domain,
    @NotNull(groups = ConsentFetchRequest.class) Set<String> policies,
    @NotBlank(groups = ConsentFetchRequest.class) String policySystem,
    @NotBlank(groups = ConsentFetchRequest.class) String patientIdentifierSystem,
    List<String> identifiers)
    implements ConsentRequest {}
