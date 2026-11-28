package care.smith.fts.util;

import static java.util.Collections.newSetFromMap;
import static java.util.Optional.ofNullable;

import java.util.IdentityHashMap;
import java.util.Set;
import org.slf4j.Logger;

public interface LogUtil {
  static void warnWithDebugException(Logger log, String msg, Throwable e) {
    if (log.isDebugEnabled()) {
      log.warn(msg, e);
    } else {
      log.warn(msg);
    }
  }

  /**
   * The types of an error and its causes, e.g. {@code
   * RetryExhaustedException(WebClientRequestException)}, for logs that must not contain patient
   * data. Error messages may contain patient data, e.g. a request URL with a patient's id.
   */
  static String errorType(Throwable e) {
    return errorType(e, newSetFromMap(new IdentityHashMap<>()));
  }

  private static String errorType(Throwable e, Set<Throwable> seen) {
    seen.add(e);
    var type = e.getClass().getSimpleName();
    return ofNullable(e.getCause())
        .filter(cause -> !seen.contains(cause))
        .map(cause -> "%s(%s)".formatted(type, errorType(cause, seen)))
        .orElse(type);
  }

  /** Logs the stack trace of an error at trace level only, as its messages may name patients. */
  static void traceException(Logger log, Throwable e) {
    if (log.isTraceEnabled()) {
      log.trace("Stack trace", e);
    }
  }
}
