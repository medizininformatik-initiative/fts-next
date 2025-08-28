package care.smith.fts.packager.exception;

import jakarta.annotation.Nullable;

public class PseudonymizerTimeoutException extends PseudonymizerException {

  public PseudonymizerTimeoutException(String message) {
    super(message);
  }

  public PseudonymizerTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  @Nullable
  public String getErrorCode() {
    return "TIMEOUT_ERROR";
  }

  @Override
  public int getSuggestedExitCode() {
    return 4; // Service unavailable (timeout)
  }
}