package care.smith.fts.util.fhir;

import static care.smith.fts.util.fhir.FhirUtils.resourceStream;

import java.util.Objects;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;

public interface FhirTag {

  /**
   * Adds a specified tag to every resource in a HAPI FHIR Bundle.
   *
   * @param bundle The HAPI FHIR Bundle to process
   * @param system The tag coding system
   * @param code The tag code
   * @param display The tag display text (optional)
   * @return The modified Bundle with tags added to every resource
   */
  static Bundle addTagToAllResources(Bundle bundle, String system, String code, String display) {
    Objects.requireNonNull(bundle);

    var tagCoding = new Coding().setSystem(system).setCode(code);
    tagCoding.setDisplay(display);

    var bundleMeta = bundle.getMeta();
    bundleMeta.addTag(tagCoding);
    bundle.setMeta(bundleMeta);

    resourceStream(bundle)
        .filter(Objects::nonNull)
        .forEach(
            resource -> {
              var meta = resource.getMeta();
              meta.addTag(tagCoding);
            });

    return bundle;
  }
}
