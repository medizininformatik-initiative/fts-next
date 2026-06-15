package care.smith.fts.rda.rest;

import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import care.smith.fts.rda.TransferProcessRunner.StartResult;
import care.smith.fts.rda.TransferProcessRunnerConfig;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class StartResponseMapper {

  private final TransferProcessRunnerConfig runnerConfig;

  public StartResponseMapper(TransferProcessRunnerConfig runnerConfig) {
    this.runnerConfig = runnerConfig;
  }

  public StartResponse fromResult(StartResult result, UriComponentsBuilder uriBuilder) {
    return switch (result) {
      case StartResult.Accepted accepted ->
          new StartResponse.Accepted(generateJobUri(uriBuilder, accepted.processId()));
      case StartResult.Rejected ignored ->
          new StartResponse.TooManyRequests(runnerConfig.retryAfterSeconds());
    };
  }

  private URI generateJobUri(UriComponentsBuilder uriBuilder, String id) {
    return uriBuilder.replacePath("api/v2/process/status/{id}").build(id);
  }

  public sealed interface StartResponse {
    ResponseEntity<Object> response();

    record Accepted(URI processUri) implements StartResponse {
      @Override
      public ResponseEntity<Object> response() {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(CONTENT_LOCATION, processUri.toString())
            .build();
      }
    }

    record TooManyRequests(int retryAfterSeconds) implements StartResponse {
      @Override
      public ResponseEntity<Object> response() {
        var response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        response.header(RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return response.build();
      }
    }
  }
}
