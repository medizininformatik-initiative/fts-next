package care.smith.fts.api.cda;

import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import care.smith.fts.api.TransportBundle;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface BundleSender extends TransferProcessStep {

  Mono<ResponseEntity<Void>> send(@NotNull TransportBundle bundle);

  interface Factory<C> extends TransferProcessStepFactory<BundleSender, Config, C> {}

  record Config() {}
}
