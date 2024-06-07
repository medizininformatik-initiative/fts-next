package care.smith.fts.util.tca;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class DateShiftingRequest {
  @NotNull(groups = DateShiftingRequest.class)
  private Set<String> ids;
}
