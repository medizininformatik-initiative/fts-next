package care.smith.fts.test;

import static care.smith.fts.test.FhirGenerators.patient;
import static care.smith.fts.test.FhirGenerators.withPrefix;
import static care.smith.fts.util.FhirUtils.toBundle;
import static java.util.stream.Stream.generate;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;

public class TestPatientGenerator {
  public static Bundle generateOnePatient(String id, String year, String identifierSystem)
      throws IOException {
    return Stream.of(patient(() -> id, () -> identifierSystem, () -> year).generateResource())
        .collect(toBundle())
        .setTotal(1);
  }

  public static BundleAndIds generateNPatients(
      String idPrefix, String year, String identifierSystem, int n) throws IOException {
    List<String> ids = generate(withPrefix(idPrefix)).limit(n).toList();
    var gen = patient(fromList(ids), () -> identifierSystem, () -> year);
    Bundle bundle = gen.generateResources().limit(n).collect(toBundle());
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
