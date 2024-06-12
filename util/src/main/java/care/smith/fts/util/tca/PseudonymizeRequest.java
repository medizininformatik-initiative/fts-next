package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.Set;
import lombok.Data;

@Data
public class PseudonymizeRequest {
  @NotNull(groups = PseudonymizeRequest.class)
  String patientId;

  @NotNull(groups = PseudonymizeRequest.class)
  Set<String> ids;

  @NotBlank(groups = PseudonymizeRequest.class)
  String domain;

  @NotNull(groups = TransportIdsRequest.class)
  Duration dateShift;
}