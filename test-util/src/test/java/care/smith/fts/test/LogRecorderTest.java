package care.smith.fts.test;

import static care.smith.fts.test.LogRecorder.messages;
import static care.smith.fts.test.LogRecorder.recordLog;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LogRecorderTest {

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(LogRecorderTest.class);

  @Test
  void recordsEventsAtLevelAndAbove() {
    var events =
        recordLog(
            LogRecorderTest.class,
            Level.DEBUG,
            () -> {
              log.trace("trace {}", 1);
              log.debug("debug {}", 2);
              log.warn("warn {}", 3);
            });

    assertThat(messages(events)).containsExactly("debug 2", "warn 3");
  }

  @Test
  void restoresLevelAndStopsRecording() {
    var logger = (Logger) LoggerFactory.getLogger(LogRecorderTest.class);
    var before = logger.getLevel();

    var events = recordLog(LogRecorderTest.class, Level.TRACE, () -> {});
    log.warn("after recording");

    assertThat(logger.getLevel()).isEqualTo(before);
    assertThat(events).isEmpty();
  }
}
