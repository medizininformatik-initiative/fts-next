package care.smith.fts.util.tca;

import java.time.Duration;
import lombok.Data;

@Data
public class PseudonymizeResponse {
  IDMap idMap;
  Duration dateShiftValue;

  public PseudonymizeResponse(IDMap idMap, Duration dateShiftValue) {
    this.idMap = idMap;
    this.dateShiftValue = dateShiftValue;
  }
}
