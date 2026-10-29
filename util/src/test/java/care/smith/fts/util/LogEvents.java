package care.smith.fts.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Records what a class logs while an action runs. The util module cannot use test-util's
 * LogRecorder, as test-util depends on util.
 */
interface LogEvents {

  static List<ILoggingEvent> recordLog(Class<?> source, Level level, Runnable action) {
    var logger = (Logger) LoggerFactory.getLogger(source);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    var originalLevel = logger.getLevel();
    logger.setLevel(level);
    try {
      action.run();
      return List.copyOf(appender.list);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
    }
  }

  static List<String> messages(List<ILoggingEvent> events) {
    return events.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
