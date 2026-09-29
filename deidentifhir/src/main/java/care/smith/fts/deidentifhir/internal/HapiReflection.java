package care.smith.fts.deidentifhir.internal;

import static java.util.stream.Collectors.toMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Type;

/**
 * Bridges HAPI's runtime property metadata to the private fields that hold the values. Typed
 * getters cannot be used for traversal because they auto-instantiate empty children, which makes an
 * absent field indistinguishable from an empty one.
 */
public interface HapiReflection {

  /**
   * Accessible fields per class, keyed by declared field name, resolved once per class. The
   * hierarchy walk, {@code setAccessible} and the shadowing rule (the most derived declaration
   * wins) all happen during the fill, so element traversal is a plain map lookup. {@link
   * ClassValue} handles concurrent access and keeps the entries tied to the class lifetime.
   */
  ClassValue<Map<String, Field>> FIELDS =
      new ClassValue<>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
          Map<String, Field> fields =
              Stream.<Class<?>>iterate(type, Objects::nonNull, Class::getSuperclass)
                  .flatMap(c -> Arrays.stream(c.getDeclaredFields()))
                  .collect(
                      toMap(Field::getName, Function.identity(), (derived, shadowed) -> derived));
          fields.values().forEach(field -> field.setAccessible(true));
          return Map.copyOf(fields);
        }
      };

  /** No-arg constructors per class, resolved once per class. */
  ClassValue<Constructor<?>> CONSTRUCTORS =
      new ClassValue<>() {
        @Override
        protected Constructor<?> computeValue(Class<?> type) {
          try {
            return type.getConstructor();
          } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Cannot instantiate empty " + type.getName(), e);
          }
        }
      };

  /**
   * One child element of a FHIR element: its HAPI property metadata, its value, and the ability to
   * write into the same slot of another instance. The storage field never leaves this type.
   */
  final class FhirChild {

    private final Property property;
    private final Field field;
    private final Object value;

    private FhirChild(Property property, Field field, Object value) {
      this.property = property;
      this.field = field;
      this.value = value;
    }

    public Property property() {
      return property;
    }

    public Object value() {
      return value;
    }

    /**
     * Writes {@code newValue} into the slot of this child on {@code target}.
     *
     * @throws IllegalArgumentException when the slot does not accept the class of {@code newValue}
     */
    public void copyInto(Base target, Object newValue) {
      try {
        field.set(target, newValue);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "%s holds %s, not %s"
                .formatted(
                    property.getName(),
                    field.getType().getSimpleName(),
                    newValue.getClass().getSimpleName()),
            e);
      }
    }
  }

  /** All children that hold a value, in metadata order. */
  static List<FhirChild> childrenWithValue(Base base) {
    Class<?> clazz = base.getClass();
    Map<String, Field> fields = FIELDS.get(clazz);
    return base.children().stream()
        .flatMap(
            property -> {
              Field field = lookup(fields, clazz, nameToField(property.getName()));
              return get(field, base).map(value -> new FhirChild(property, field, value)).stream();
            })
        .toList();
  }

  /** Choice elements are named {@code value[x]} in metadata; the concrete type names the path. */
  static String toPathElement(Property property, Object value) {
    String name = property.getName();
    if (name.endsWith("[x]")) {
      return name.substring(0, name.length() - 3) + "[" + ((Type) value).fhirType() + "]";
    }
    return name;
  }

  /**
   * HAPI suffixes fields whose FHIR name is a Java keyword, e.g. {@code class} → {@code class_}.
   */
  static String nameToField(String name) {
    if (name.endsWith("[x]")) {
      return name.substring(0, name.length() - 3);
    }
    if (SourceVersion.isKeyword(name)) {
      return name + "_";
    }
    return name;
  }

  static <T> T newEmptyInstance(Class<T> clazz) {
    try {
      return clazz.cast(CONSTRUCTORS.get(clazz).newInstance());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot instantiate empty " + clazz.getName(), e);
    }
  }

  private static Field lookup(Map<String, Field> fields, Class<?> clazz, String name) {
    return Optional.ofNullable(fields.get(name))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No field '%s' found on %s or its superclasses"
                        .formatted(name, clazz.getName())));
  }

  private static Optional<Object> get(Field field, Object target) {
    try {
      return Optional.ofNullable(field.get(target));
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
