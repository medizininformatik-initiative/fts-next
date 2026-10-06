package care.smith.fts.deidentifier.internal;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Extension;

/** Resolves a profile path to the HAPI class of the element it names, from the R4 definitions. */
public interface FhirPaths {

  /**
   * The class of the element at {@code path}, e.g. {@code DateType} for {@code Patient.birthDate}.
   */
  static Optional<Class<?>> elementType(String path) {
    List<String> elements = List.of(path.split("\\.", -1));
    return resource(elements.getFirst())
        .flatMap(resource -> descend(resource, elements.subList(1, elements.size())))
        .map(BaseRuntimeElementDefinition::getImplementingClass);
  }

  /** HAPI looks resource types up case-insensitively, the engine names them exactly. */
  private static Optional<BaseRuntimeElementDefinition<?>> resource(String name) {
    try {
      return Optional.<BaseRuntimeElementDefinition<?>>of(
              FhirContext.forR4Cached().getResourceDefinition(name))
          .filter(definition -> definition.getName().equals(name));
    } catch (DataFormatException | IllegalArgumentException unknownResourceType) {
      return Optional.empty();
    }
  }

  /** Follows the remaining path elements from {@code parent}, one child per element. */
  private static Optional<BaseRuntimeElementDefinition<?>> descend(
      BaseRuntimeElementDefinition<?> parent, List<String> elements) {
    if (elements.isEmpty()) {
      return Optional.of(parent);
    }
    return child(parent, elements.getFirst())
        .flatMap(child -> descend(child, elements.subList(1, elements.size())));
  }

  /**
   * The definition of one child. A primitive has no child definitions, but its {@code extension} is
   * still an Extension.
   */
  private static Optional<BaseRuntimeElementDefinition<?>> child(
      BaseRuntimeElementDefinition<?> parent, String element) {
    if (!(parent instanceof BaseRuntimeElementCompositeDefinition<?> composite)) {
      return Optional.of(element)
          .filter("extension"::equals)
          .map(extension -> FhirContext.forR4Cached().getElementDefinition(Extension.class));
    }
    Matcher bracket = Pattern.compile("([^\\[\\]]+)\\[([^\\[\\]]+)]").matcher(element);
    Optional<String> type = Optional.of(bracket).filter(Matcher::matches).map(m -> m.group(2));
    String name = type.isPresent() ? bracket.group(1) : element;
    return composite.getChildren().stream()
        .filter(child -> child.getElementName().equals(name))
        .findFirst()
        .flatMap(child -> definition(child, type));
  }

  /**
   * The definition of a child as the engine names it: a choice element only with its FHIR type, as
   * in {@code value[Quantity]}, any other element only without. HAPI spells the choice differently,
   * {@code valueQuantity}, and none of its names is the bare element name.
   */
  private static Optional<BaseRuntimeElementDefinition<?>> definition(
      BaseRuntimeChildDefinition child, Optional<String> type) {
    boolean choice = !child.getValidChildNames().contains(child.getElementName());
    if (choice != type.isPresent()) {
      return Optional.empty();
    }
    return child.getValidChildNames().stream()
        .<BaseRuntimeElementDefinition<?>>map(child::getChildByName)
        .filter(definition -> type.map(definition.getName()::equals).orElse(true))
        .findFirst();
  }
}
