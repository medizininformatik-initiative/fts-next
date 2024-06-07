package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.HashSet;

@Data
public class ConsentRequest {
  @NotNull(groups = ConsentRequest.class)
  String domain;

  @NotNull(groups = ConsentRequest.class)
  HashSet<String> policies;
}
