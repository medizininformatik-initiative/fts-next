package care.smith.fts.deidentifhir;

import care.smith.fts.deidentifhir.internal.HapiReflection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Resource;

/** The restricted FHIRPath subset that selects which resources a module applies to. */
public sealed interface FhirPathPattern {

  boolean matches(Resource resource);

  Pattern PROFILE = Pattern.compile("(.*)\\.meta\\.profile contains '(.*)'");
  Pattern RESOURCE_EXISTS = Pattern.compile("(.*)\\.exists\\(\\)");
  Pattern IDENTIFIER_SYSTEM = Pattern.compile("(.*)\\.identifier\\.system contains '(.*)'");

  static FhirPathPattern parse(String fhirPath) {
    Matcher profile = PROFILE.matcher(fhirPath);
    if (profile.matches()) {
      return new ProfileFhirPath(profile.group(1), profile.group(2));
    }
    Matcher exists = RESOURCE_EXISTS.matcher(fhirPath);
    if (exists.matches()) {
      return new ResourceExistsPath(exists.group(1));
    }
    Matcher identifierSystem = IDENTIFIER_SYSTEM.matcher(fhirPath);
    if (identifierSystem.matches()) {
      return new IdentifierSystemFhirPath(identifierSystem.group(1), identifierSystem.group(2));
    }
    throw new IllegalArgumentException("Pattern '%s' cannot be parsed.".formatted(fhirPath));
  }

  record ProfileFhirPath(String resourceType, String canonicalProfile) implements FhirPathPattern {

    /**
     * A resource may claim a profile with the version it was written against, {@code
     * …/Diagnose|1.0.4}, while the module names the profile alone. An unversioned pattern therefore
     * matches every version of its profile, and a pattern that does name a version matches that
     * version only.
     */
    @Override
    public boolean matches(Resource resource) {
      if (!resource.getResourceType().toString().equals(resourceType)) {
        return false;
      }
      return resource.getMeta().getProfile().stream()
          .map(CanonicalType::getValue)
          .filter(Objects::nonNull)
          .anyMatch(this::matchesProfile);
    }

    private boolean matchesProfile(String profile) {
      return profile.equals(canonicalProfile)
          || (!canonicalProfile.contains("|") && profile.startsWith(canonicalProfile + "|"));
    }
  }

  record ResourceExistsPath(String resourceType) implements FhirPathPattern {
    @Override
    public boolean matches(Resource resource) {
      return resource.getResourceType().toString().equals(resourceType);
    }
  }

  record IdentifierSystemFhirPath(String resourceType, String identifierSystem)
      implements FhirPathPattern {
    /**
     * There is no common base class declaring {@code getIdentifier()}, so the identifier list is
     * read via reflection.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean matches(Resource resource) {
      if (!resource.getResourceType().toString().equals(resourceType)) {
        return false;
      }
      return HapiReflection.getChild(resource, "identifier").stream()
          .flatMap(identifiers -> ((List<Identifier>) identifiers).stream())
          .anyMatch(identifier -> identifierSystem.equals(identifier.getSystem()));
    }
  }
}
