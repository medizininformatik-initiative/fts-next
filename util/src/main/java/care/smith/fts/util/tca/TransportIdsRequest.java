package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Data;

@Data
public class TransportIdsRequest {
  @NotNull(groups = TransportIdsRequest.class)
  Set<String> ids;

  @NotBlank(groups = TransportIdsRequest.class)
  String domain;
}
