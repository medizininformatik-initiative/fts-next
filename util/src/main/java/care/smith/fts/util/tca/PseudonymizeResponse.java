package care.smith.fts.util.tca;

import lombok.Data;

import java.time.Duration;

@Data
public class PseudonymizeResponse {
  TransportIDs transportIDs;
  Duration dateShiftValue;

  public PseudonymizeResponse(TransportIDs transportIDs, Duration dateShiftValue) {
    this.transportIDs = transportIDs;
    this.dateShiftValue = dateShiftValue;
  }
}
