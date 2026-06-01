package care.smith.fts.rda;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Implements tier-1 (global hard cap) and tier-2 (elastic, work-conserving per-project fair share)
 * of the RDA admission policy.
 *
 * <p>The fair share is not a static cap: it is recomputed at every admission against the number of
 * currently-active projects, so a lone project may fill the whole buffer while two active projects
 * each get half. In-flight bundles above a newly-shrunk share are never evicted; they drain
 * naturally.
 *
 * <p>Invariant: {@code permitsInUse() == totalUsage() == Σ usage[P]}. The {@link #release} call
 * must fire exactly once per admitted bundle on its terminal signal.
 */
final class AdmissionController {

  private final int globalBufferMax;
  private final Semaphore globalSem;
  private final Map<String, Integer> usage = new HashMap<>();

  AdmissionController(int globalBufferMax) {
    this.globalBufferMax = globalBufferMax;
    this.globalSem = new Semaphore(globalBufferMax);
  }

  enum AdmitResult {
    ACCEPT,
    REJECT
  }

  synchronized AdmitResult admit(String project) {
    if (!globalSem.tryAcquire()) {
      return AdmitResult.REJECT; // tier-1: global hard cap reached
    }
    int current = usage.getOrDefault(project, 0);
    int active = usage.size();
    if (current == 0) {
      active++; // include the arriving project itself
    }
    int fairShare = Math.ceilDiv(globalBufferMax, active);
    if (current >= fairShare) {
      globalSem.release(); // tier-2: over your elastic share, give the permit back
      return AdmitResult.REJECT;
    }
    usage.put(project, current + 1);
    return AdmitResult.ACCEPT;
  }

  synchronized void release(String project) {
    // usage only ever holds values >= 1 (keys are removed at count 1), so an absent key (null) is
    // the sole "no matching admit" case; a stored 0 is unreachable.
    Integer current = usage.get(project);
    if (current == null) {
      throw new IllegalStateException("release without matching admit for project: " + project);
    }
    if (current == 1) {
      usage.remove(project);
    } else {
      usage.put(project, current - 1);
    }
    globalSem.release();
  }

  synchronized int usage(String project) {
    return usage.getOrDefault(project, 0);
  }

  synchronized int totalUsage() {
    return usage.values().stream().mapToInt(Integer::intValue).sum();
  }

  int permitsInUse() {
    return globalBufferMax - globalSem.availablePermits();
  }
}
