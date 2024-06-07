package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Data;

@Data
public class PseudonymRequest {
  @NotNull(groups = PseudonymRequest.class)
  Set<String> ids;
}
