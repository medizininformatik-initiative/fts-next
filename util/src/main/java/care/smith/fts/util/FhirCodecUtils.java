package care.smith.fts.util;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.core.ResolvableType;

public class FhirCodecUtils {
  public static Class<? extends IBaseResource> ensureBaseResource(ResolvableType type) {
    if (isBaseResource(type.getRawClass())) {
      return (Class<? extends IBaseResource>) type.getRawClass();
    } else {
      throw new IllegalArgumentException("Unsupported resource type: " + type.getRawClass());
    }
  }

  public static boolean isBaseResource(Class<?> clazz) {
    return clazz != null && IBaseResource.class.isAssignableFrom(clazz);
  }
}
