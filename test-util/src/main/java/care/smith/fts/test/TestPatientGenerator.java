package care.smith.fts.test;

import care.smith.fts.test.FhirGenerator.Incrementing;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;

public class TestPatientGenerator {
  public static Bundle generateOnePatient(String id, String year, String identifierSystem)
      throws IOException {
    return FhirGenerator.patient(() -> id, () -> identifierSystem, () -> year)
        .generateBundle(1, 100);
  }

  public static BundleAndIds generateNPatients(
      String idPrefix, String year, String identifierSystem, int n) throws IOException {
    List<String> ids = Stream.generate(Incrementing.withPrefix(idPrefix)).limit(n).toList();
    var gen = FhirGenerator.patient(fromList(ids), () -> identifierSystem, () -> year);
    Bundle bundle = gen.generateBundle(n, n);
    return new BundleAndIds(bundle, ids);
  }

  public record BundleAndIds(Bundle bundle, List<String> ids) {}

  static <T> Supplier<T> fromList(List<T> list) {
    return new Supplier<>() {
      int i = 0;

      @Override
      public T get() {
        return list.get(i++);
      }
    };
  }
}
