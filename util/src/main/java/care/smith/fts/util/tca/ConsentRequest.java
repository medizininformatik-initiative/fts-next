package care.smith.fts.util.tca;

import java.util.Set;

public interface ConsentRequest {
  String policySystem();

  Set<String> policies();

  String domain();
}
