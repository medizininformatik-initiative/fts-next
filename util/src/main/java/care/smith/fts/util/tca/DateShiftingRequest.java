package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.Set;
import lombok.Data;

@Data
public class DateShiftingRequest {
  @NotNull(groups = DateShiftingRequest.class)
  private Set<String> ids;

  @NotNull(groups = DateShiftingRequest.class)
  Duration dateShift;
}
