package care.smith.fts.util.selfassessment;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComponentStatus(
    String name, String kind, String url, Status status, Long latencyMs, String reason) {

  public static ComponentStatus up(String name, String kind, String url, long latencyMs) {
    return new ComponentStatus(name, kind, url, Status.UP, latencyMs, null);
  }

  public static ComponentStatus down(String name, String kind, String url, String reason) {
    return new ComponentStatus(name, kind, url, Status.DOWN, null, reason);
  }

  public static ComponentStatus degraded(
      String name, String kind, String url, long latencyMs, String reason) {
    return new ComponentStatus(name, kind, url, Status.DEGRADED, latencyMs, reason);
  }

  public static ComponentStatus skipped(String name, String kind, String reason) {
    return new ComponentStatus(name, kind, null, Status.SKIPPED, null, reason);
  }
}
