package care.smith.fts.cda.services.deidentifhir;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NamespacingServiceTest {

  @Test
  void getKeyForResourceTypeAndID() {
    NamespacingService namespacingService = new NamespacingService("test");
    assertEquals(
        "test.id.Patient:patientID",
        namespacingService.getKeyForResourceTypeAndID("Patient", "patientID"));
  }

  @Test
  void getKeyForSystemAndValue() {
    NamespacingService namespacingService = new NamespacingService("test");
    assertEquals(
        "test.identifier.Patient:patientID",
        namespacingService.getKeyForSystemAndValue("Patient", "patientID"));
  }
}
