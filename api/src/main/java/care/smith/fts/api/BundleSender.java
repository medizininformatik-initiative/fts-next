package care.smith.fts.api;

import org.hl7.fhir.instance.model.api.IBaseBundle;

public interface BundleSender<B extends IBaseBundle> {

  boolean send(B bundle, String project);

  interface Factory<B extends IBaseBundle, C> extends StepFactory<BundleSender<B>, Config, C> {}

  record Config() {}
}
