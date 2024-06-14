package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import lombok.Data;

@Data
public class ConsentRequest {
  @NotNull(groups = ConsentRequest.class)
  String domain;

  @NotNull(groups = ConsentRequest.class)
  HashSet<String> policies;
}
