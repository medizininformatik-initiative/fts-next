package care.smith.fts.util;

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
   * The type of an error, for logs that must not contain patient data. Error messages may contain
   * patient data, e.g. a request URL with a patient's id.
   */
  static String errorType(Throwable e) {
    return e.getClass().getSimpleName();
  }

  /** Logs the stack trace of an error at trace level only, as its messages may name patients. */
  static void traceException(Logger log, Throwable e) {
    if (log.isTraceEnabled()) {
      log.trace("Stack trace", e);
    }
  }
}
