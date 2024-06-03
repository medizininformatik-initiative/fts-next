package care.smith.fts.api;

import org.hl7.fhir.instance.model.api.IBaseBundle;

public interface BundleSender {

  boolean send(IBaseBundle bundle);

  interface Factory<C> extends StepFactory<BundleSender, Config, C> {}

  record Config() {}
}
