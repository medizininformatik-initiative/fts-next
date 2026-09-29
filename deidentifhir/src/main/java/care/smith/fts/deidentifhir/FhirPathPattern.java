package care.smith.fts.deidentifhir;

import ca.uhn.fhir.context.FhirContext;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Resource;

/** The restricted FHIRPath subset that selects which resources a module applies to. */
public sealed interface FhirPathPattern {

  boolean matches(Resource resource);

  /** The resource type the pattern selects from; resources of any other type never match. */
  String resourceType();

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
      return new IdentifierSystemFhirPath(
          requireIdentifier(identifierSystem.group(1)), identifierSystem.group(2));
    }
    throw new IllegalArgumentException("Pattern '%s' cannot be parsed.".formatted(fhirPath));
  }

  /** A pattern on a type without an identifier element can never match. */
  private static String requireIdentifier(String resourceType) {
    if (FhirContext.forR4Cached()
            .getResourceDefinition(resourceType)
            .getChildByName("identifier")
        == null) {
      throw new IllegalArgumentException(
          "Resource type %s has no identifier element to match a system on."
              .formatted(resourceType));
    }
    return resourceType;
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
     * There is no common base class declaring {@code getIdentifier()}, and the element is a list on
     * most resource types but a single element on some, e.g. QuestionnaireResponse. The named
     * property answers both as a list of values without creating the element.
     */
    @Override
    public boolean matches(Resource resource) {
      if (!resource.getResourceType().toString().equals(resourceType)) {
        return false;
      }
      return Optional.ofNullable(resource.getNamedProperty("identifier")).stream()
          .flatMap(identifiers -> identifiers.getValues().stream())
          .map(Identifier.class::cast)
          .anyMatch(identifier -> identifierSystem.equals(identifier.getSystem()));
    }
  }
}
