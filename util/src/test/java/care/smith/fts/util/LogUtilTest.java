package care.smith.fts.util;

import static care.smith.fts.util.LogUtil.errorType;
import static care.smith.fts.util.LogUtil.traceException;
import static care.smith.fts.util.LogUtil.warnWithDebugException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class LogUtilTest {

  @Mock Logger log;

  @Test
  void debugHasException() {
    var msg = "message-115091";
    var e = new Exception("message-115012");
    given(log.isDebugEnabled()).willReturn(true);

    warnWithDebugException(log, msg, e);

    verify(log).warn(msg, e);
  }

  @Test
  void errorTypeIsSimpleClassNameWithoutMessage() {
    var e = new IllegalStateException("patient-114562");

    assertThat(errorType(e)).isEqualTo("IllegalStateException");
  }

  @Test
  void traceExceptionLogsStackTraceAtTrace() {
    var e = new Exception("message-114562");
    given(log.isTraceEnabled()).willReturn(true);

    traceException(log, e);

    verify(log).trace("Stack trace", e);
  }

  @Test
  void traceExceptionLogsNothingBelowTrace() {
    var e = new Exception("message-114562");
    given(log.isTraceEnabled()).willReturn(false);

    traceException(log, e);

    verify(log).isTraceEnabled();
    verifyNoMoreInteractions(log);
  }

  @Test
  void noDebugHasNoException() {
    var msg = "message-114539";
    var e = new Exception("message-114562");
    given(log.isDebugEnabled()).willReturn(false);

    warnWithDebugException(log, msg, e);

    verify(log).warn(msg);
  }
}
