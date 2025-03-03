package care.smith.fts.util;

import static care.smith.fts.util.LogUtil.warnWithDebugException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
  void noDebugHasNoException() {
    var msg = "message-114539";
    var e = new Exception("message-114562");
    given(log.isDebugEnabled()).willReturn(false);

    warnWithDebugException(log, msg, e);

    verify(log).warn(msg);
  }
}
