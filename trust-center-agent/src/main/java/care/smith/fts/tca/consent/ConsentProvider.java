package care.smith.fts.tca.consent;

import care.smith.fts.api.ConsentedPatient;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

public interface ConsentProvider {
  List<ConsentedPatient> allConsentedPatients(String domain, HashSet<String> policies)
      throws IOException;
}
