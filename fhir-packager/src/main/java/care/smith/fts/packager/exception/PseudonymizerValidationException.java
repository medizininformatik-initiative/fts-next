package care.smith.fts.packager.exception;

import jakarta.annotation.Nullable;

public class PseudonymizerValidationException extends PseudonymizerException {

  private final int httpStatus;

  public PseudonymizerValidationException(String message, int httpStatus) {
    super(message);
    this.httpStatus = httpStatus;
  }

  public PseudonymizerValidationException(String message, int httpStatus, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  @Override
  @Nullable
  public String getErrorCode() {
    return "VALIDATION_ERROR_" + httpStatus;
  }

  @Override
  public int getSuggestedExitCode() {
    return 3; // Invalid input data
  }
}