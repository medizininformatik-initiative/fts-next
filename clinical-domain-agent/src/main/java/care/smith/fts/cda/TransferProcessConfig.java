package care.smith.fts.cda;

import java.util.Map;
import lombok.*;

@Data
public class TransferProcessConfig {
  private Map<String, Object> cohortSelector;
  private Map<String, Object> dataSelector;
  private Map<String, Object> deidentificationProvider;
  private Map<String, Object> bundleSender;
}
