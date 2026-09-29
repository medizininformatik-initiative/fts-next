package care.smith.fts.deidentifhir.internal;

import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import java.util.Optional;
import org.hl7.fhir.r4.model.Extension;

/** Resolves a profile path to the HAPI class of the element it names, from the R4 definitions. */
public interface FhirPaths {

  /**
   * The class of the element at {@code path}, e.g. {@code DateType} for {@code Patient.birthDate}
   * or {@code Quantity} for {@code Observation.value[Quantity]}. Empty when the path names no
   * element: an unknown element, or a choice element without its type, which the engine never
   * visits under that name.
   */
  static Optional<Class<?>> elementType(String path) {
    String[] elements = path.split("\\.");
    FhirContext context = FhirContext.forR4Cached();
    Optional<BaseRuntimeElementDefinition<?>> definition;
    try {
      definition = Optional.of(context.getResourceDefinition(elements[0]));
    } catch (RuntimeException unknownResourceType) {
      return Optional.empty();
    }
    for (int i = 1; i < elements.length; i++) {
      String element = elements[i];
      definition = definition.flatMap(parent -> child(context, parent, element));
    }
    return definition.map(BaseRuntimeElementDefinition::getImplementingClass);
  }

  /**
   * The definition of one child. A choice element {@code value[Quantity]} is named {@code
   * valueQuantity} in HAPI. A primitive has no child definitions, but its {@code extension} is
   * still an Extension.
   */
  private static Optional<BaseRuntimeElementDefinition<?>> child(
      FhirContext context, BaseRuntimeElementDefinition<?> parent, String element) {
    if (!(parent instanceof BaseRuntimeElementCompositeDefinition<?> composite)) {
      return Optional.of(element)
          .filter("extension"::equals)
          .map(extension -> context.getElementDefinition(Extension.class));
    }
    String name = hapiName(element);
    return Optional.ofNullable(composite.getChildByName(name))
        .map(child -> child.getChildByName(name));
  }

  private static String hapiName(String element) {
    int bracket = element.indexOf('[');
    if (bracket < 0) {
      return element;
    }
    String type = element.substring(bracket + 1, element.length() - 1);
    return element.substring(0, bracket)
        + Character.toUpperCase(type.charAt(0))
        + type.substring(1);
  }
}
