package care.smith.fts.deidentifier.allowlist;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.parser.DataFormatException;
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

  Pattern RESOURCE_EXISTS = Pattern.compile("(.*)\\.exists\\(\\)");
  Pattern PROFILE = Pattern.compile("(.*)\\.meta\\.profile contains '(.*)'");
  Pattern IDENTIFIER_SYSTEM = Pattern.compile("(.*)\\.identifier\\.system contains '(.*)'");

  static FhirPathPattern parse(String fhirPath) {
    Matcher exists = RESOURCE_EXISTS.matcher(fhirPath);
    if (exists.matches()) {
      return new ResourceExistsPath(requireResourceType(fhirPath, exists.group(1)));
    }
    Matcher profile = PROFILE.matcher(fhirPath);
    if (profile.matches()) {
      return new ProfileFhirPath(requireResourceType(fhirPath, profile.group(1)), profile.group(2));
    }
    Matcher identifierSystem = IDENTIFIER_SYSTEM.matcher(fhirPath);
    if (identifierSystem.matches()) {
      return new IdentifierSystemFhirPath(
          requireIdentifier(fhirPath, identifierSystem.group(1)), identifierSystem.group(2));
    }
    throw new IllegalArgumentException("Pattern '%s' cannot be parsed.".formatted(fhirPath));
  }

  private static boolean isOfType(Resource resource, String resourceType) {
    return resource.getResourceType().toString().equals(resourceType);
  }

  /**
   * The R4 definition of the type a pattern names. A pattern on a type that the engine's R4 model
   * does not have can never match. HAPI looks the name up regardless of case, so the exact name is
   * compared as well: {@code patient} resolves to {@code Patient}, but no resource has that type.
   */
  private static RuntimeResourceDefinition definition(String fhirPath, String resourceType) {
    return lookUp(resourceType)
        .filter(definition -> definition.getName().equals(resourceType))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Pattern '%s' names no FHIR R4 resource type: %s"
                        .formatted(fhirPath, resourceType)));
  }

  private static Optional<RuntimeResourceDefinition> lookUp(String resourceType) {
    try {
      return Optional.of(FhirContext.forR4Cached().getResourceDefinition(resourceType));
    } catch (DataFormatException unknownResourceType) {
      return Optional.empty();
    }
  }

  private static String requireResourceType(String fhirPath, String resourceType) {
    definition(fhirPath, resourceType);
    return resourceType;
  }

  /** A pattern on a type without an identifier element can never match. */
  private static String requireIdentifier(String fhirPath, String resourceType) {
    if (definition(fhirPath, resourceType).getChildByName("identifier") == null) {
      throw new IllegalArgumentException(
          "Resource type %s has no identifier element to match a system on."
              .formatted(resourceType));
    }
    return resourceType;
  }

  record ResourceExistsPath(String resourceType) implements FhirPathPattern {
    @Override
    public boolean matches(Resource resource) {
      return isOfType(resource, resourceType);
    }
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
      return isOfType(resource, resourceType)
          && resource.getMeta().getProfile().stream()
              .map(CanonicalType::getValue)
              .filter(Objects::nonNull)
              .anyMatch(this::matchesProfile);
    }

    private boolean matchesProfile(String profile) {
      // a versioned pattern cannot be a prefix of another version: "P|1.0|" never starts a claim
      return canonicalProfile.equals(profile) || profile.startsWith(canonicalProfile + "|");
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
      return isOfType(resource, resourceType)
          && resource.getNamedProperty("identifier").getValues().stream()
              .map(Identifier.class::cast)
              .anyMatch(identifier -> identifierSystem.equals(identifier.getSystem()));
    }
  }
}
