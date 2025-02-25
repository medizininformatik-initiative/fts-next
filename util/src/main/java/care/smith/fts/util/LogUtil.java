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
}
