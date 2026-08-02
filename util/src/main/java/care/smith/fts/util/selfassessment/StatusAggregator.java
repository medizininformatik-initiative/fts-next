package care.smith.fts.util.selfassessment;

import java.util.stream.Stream;

public interface StatusAggregator {

  static Status worstOf(Stream<Status> statuses) {
    return statuses.reduce(StatusAggregator::worse).orElse(Status.UP);
  }

  private static Status worse(Status a, Status b) {
    return rank(a) >= rank(b) ? a : b;
  }

  private static int rank(Status s) {
    return switch (s) {
      case SKIPPED -> 0;
      case UP -> 1;
      case DEGRADED -> 2;
      case DOWN -> 3;
    };
  }
}
