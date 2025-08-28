package care.smith.fts.packager.exception;

import jakarta.annotation.Nullable;

public class PseudonymizerResponseException extends PseudonymizerException {

  public PseudonymizerResponseException(String message) {
    super(message);
  }

  public PseudonymizerResponseException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  @Nullable
  public String getErrorCode() {
    return "RESPONSE_ERROR";
  }

  @Override
  public int getSuggestedExitCode() {
    return 1; // General error
  }
}