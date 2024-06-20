package care.smith.fts.tca.deidentification;

import static java.util.stream.Collectors.toMap;

import care.smith.fts.tca.deidentification.GpasParameterResponse.Parameter.OriginalAndPseudonym;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Record to deserialize gPAS response from $pseudonymizeAllowCreate operation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GpasParameterResponse(String resourceType, List<Parameter> parameter) {

  public Map<String, String> getMappedID() {
    return parameter.stream()
        .map(Parameter::getOriginalAndPseudonym)
        .map(Optional::orElseThrow)
        .collect(toMap(OriginalAndPseudonym::original, OriginalAndPseudonym::pseudonym));
  }

  record Parameter(String name, List<Part> part) {

    Optional<OriginalAndPseudonym> getOriginalAndPseudonym() {
      Optional<Part> original = part.stream().filter(p -> p.name.equals("original")).findFirst();
      if (original.isPresent()) {
        Optional<Part> pseudonym =
            part.stream().filter(p -> p.name.equals("pseudonym")).findFirst();
        if (pseudonym.isPresent()) {
          return Optional.of(
              new OriginalAndPseudonym(
                  original.get().valueIdentifier.value, pseudonym.get().valueIdentifier.value));
        }
      }
      return Optional.empty();
    }

    record OriginalAndPseudonym(String original, String pseudonym) {}

    record Part(String name, ValueIdentifier valueIdentifier) {
      @JsonIgnoreProperties(ignoreUnknown = true)
      record ValueIdentifier(String value) {}
    }
  }
}
