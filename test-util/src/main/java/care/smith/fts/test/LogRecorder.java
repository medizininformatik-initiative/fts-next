package care.smith.fts.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Records what a class logs while an action runs. */
public interface LogRecorder {

  /**
   * Records the log events of the logger of {@code source} at {@code level} and above, while {@code
   * action} runs. Restores the logger's level afterwards.
   */
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

  /** The formatted messages of the events. */
  static List<String> messages(List<ILoggingEvent> events) {
    return events.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  /** The messages of the exceptions logged with the events. */
  static List<String> stackTraces(List<ILoggingEvent> events) {
    return events.stream()
        .filter(e -> e.getThrowableProxy() != null)
        .map(e -> e.getThrowableProxy().getMessage())
        .toList();
  }
}
