package care.smith.fts.deidentifier.allowlist;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Resource;

/** The restricted FHIRPath subset that selects which resources a module applies to. */
public sealed interface FhirPathPattern {

  boolean matches(Resource resource);

  Pattern RESOURCE_EXISTS = Pattern.compile("(.*)\\.exists\\(\\)");

  static FhirPathPattern parse(String fhirPath) {
    Matcher exists = RESOURCE_EXISTS.matcher(fhirPath);
    if (exists.matches()) {
      return new ResourceExistsPath(exists.group(1));
    }
    throw new IllegalArgumentException("Pattern '%s' cannot be parsed.".formatted(fhirPath));
  }

  record ResourceExistsPath(String resourceType) implements FhirPathPattern {
    @Override
    public boolean matches(Resource resource) {
      return resource.getResourceType().toString().equals(resourceType);
    }
  }
}
