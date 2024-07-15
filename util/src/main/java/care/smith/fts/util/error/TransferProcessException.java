package care.smith.fts.util.error;

public class TransferProcessException extends Exception {

  public TransferProcessException(String message) {
    super(message);
  }

  public TransferProcessException(String message, Throwable cause) {
    super(message, cause);
  }
}
