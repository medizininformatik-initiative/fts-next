package care.smith.fts.packager.exception;

import jakarta.annotation.Nullable;

public abstract class PseudonymizerException extends RuntimeException {

  protected PseudonymizerException(String message) {
    super(message);
  }

  protected PseudonymizerException(String message, Throwable cause) {
    super(message, cause);
  }

  @Nullable
  public abstract String getErrorCode();

  public abstract int getSuggestedExitCode();
}