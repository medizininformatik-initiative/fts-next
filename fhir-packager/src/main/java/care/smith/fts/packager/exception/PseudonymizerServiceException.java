package care.smith.fts.packager.exception;

import jakarta.annotation.Nullable;

public class PseudonymizerServiceException extends PseudonymizerException {

  private final int httpStatus;

  public PseudonymizerServiceException(String message, int httpStatus) {
    super(message);
    this.httpStatus = httpStatus;
  }

  public PseudonymizerServiceException(String message, int httpStatus, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  @Override
  @Nullable
  public String getErrorCode() {
    return "SERVICE_ERROR_" + httpStatus;
  }

  @Override
  public int getSuggestedExitCode() {
    return 1; // General error
  }
}