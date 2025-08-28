package care.smith.fts.packager.exception;

import jakarta.annotation.Nullable;

public class PseudonymizerConnectionException extends PseudonymizerException {

  public PseudonymizerConnectionException(String message) {
    super(message);
  }

  public PseudonymizerConnectionException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  @Nullable
  public String getErrorCode() {
    return "CONNECTION_ERROR";
  }

  @Override
  public int getSuggestedExitCode() {
    return 4; // Service unavailable
  }
}