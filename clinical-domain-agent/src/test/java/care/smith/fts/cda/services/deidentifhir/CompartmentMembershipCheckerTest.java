package care.smith.fts.cda.services.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import care.smith.fts.cda.services.PatientCompartmentService;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompartmentMembershipCheckerTest {

  private static final String PATIENT_ID = "patient123";

  private CompartmentMembershipChecker checker;

  @BeforeEach
  void setUp() {
    // ServiceRequest has params ["subject", "performer"] according to compartment definition
    // Organization has no params (never in compartment)
    var compartmentService =
        new PatientCompartmentService(
            Map.of(
                "ServiceRequest", List.of("subject", "performer"),
                "Organization", List.of(),
                "Observation", List.of("subject", "performer")));
    checker = new CompartmentMembershipChecker(compartmentService);
  }

  @Nested
  @DisplayName("ServiceRequest compartment membership")
  class ServiceRequestTests {

    @Test
    @DisplayName("subject references patient -> IN compartment")
    void subjectReferencesPatient_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr1");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("performer references patient -> IN compartment")
    void performerReferencesPatient_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr2");
      sr.setSubject(new Reference("Group/group1"));
      sr.addPerformer(new Reference("Patient/" + PATIENT_ID));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("both subject and performer reference patient -> IN compartment")
    void bothReferencePatient_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr3");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID));
      sr.addPerformer(new Reference("Patient/" + PATIENT_ID));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("neither subject nor performer references patient -> NOT in compartment")
    void neitherReferencesPatient_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr4");
      sr.setSubject(new Reference("Group/group1"));
      sr.addPerformer(new Reference("Organization/org1"));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName(
        "other field (requester) references patient, but not a param -> NOT in compartment")
    void otherFieldReferencesPatient_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr5");
      sr.setSubject(new Reference("Group/group1"));
      sr.addPerformer(new Reference("Organization/org1"));
      sr.setRequester(new Reference("Patient/" + PATIENT_ID)); // Not a compartment param!

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("subject references different patient -> NOT in compartment")
    void subjectReferencesDifferentPatient_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr6");
      sr.setSubject(new Reference("Patient/differentPatient"));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("empty ServiceRequest -> NOT in compartment")
    void emptyServiceRequest_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr7");

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }
  }

  @Nested
  @DisplayName("Organization compartment membership")
  class OrganizationTests {

    @Test
    @DisplayName("Organization has no params -> never in compartment")
    void organizationNeverInCompartment() {
      var org = new Organization();
      org.setId("org1");

      assertThat(checker.isInPatientCompartment(org, PATIENT_ID)).isFalse();
    }
  }

  @Nested
  @DisplayName("Patient compartment membership")
  class PatientTests {

    @Test
    @DisplayName("Patient resource IS the patient -> IN compartment")
    void patientResourceIsPatient_isInCompartment() {
      var patient = new org.hl7.fhir.r4.model.Patient();
      patient.setId(PATIENT_ID);

      assertThat(checker.isInPatientCompartment(patient, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("Patient resource is different patient -> NOT in compartment")
    void patientResourceIsDifferentPatient_notInCompartment() {
      var patient = new org.hl7.fhir.r4.model.Patient();
      patient.setId("differentPatient");

      assertThat(checker.isInPatientCompartment(patient, PATIENT_ID)).isFalse();
    }
  }

  @Nested
  @DisplayName("Multiple performers")
  class MultiplePerformersTests {

    @Test
    @DisplayName("one of multiple performers references patient -> IN compartment")
    void oneOfMultiplePerformersReferencesPatient_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr8");
      sr.setSubject(new Reference("Group/group1"));
      sr.addPerformer(new Reference("Practitioner/practitioner1"));
      sr.addPerformer(new Reference("Patient/" + PATIENT_ID));
      sr.addPerformer(new Reference("Organization/org1"));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }
  }

  @Nested
  @DisplayName("Edge cases for reference handling")
  class ReferenceEdgeCasesTests {

    @Test
    @DisplayName("null reference -> NOT in compartment")
    void nullReference_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr9");
      sr.setSubject(null);

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("empty reference -> NOT in compartment")
    void emptyReference_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr10");
      sr.setSubject(new Reference());

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("reference with null value -> NOT in compartment")
    void referenceWithNullValue_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr11");
      var ref = new Reference();
      ref.setDisplay("Some display"); // Has display but no reference value
      sr.setSubject(ref);

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("reference to non-Patient resource type -> NOT in compartment")
    void referenceToNonPatient_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr12");
      sr.setSubject(new Reference("RelatedPerson/" + PATIENT_ID));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("invalid param name does not cause exception")
    void invalidParamName_handledGracefully() {
      var compartmentService =
          new PatientCompartmentService(Map.of("ServiceRequest", List.of("nonExistentParam")));
      var checkerWithInvalidParam = new CompartmentMembershipChecker(compartmentService);

      var sr = new ServiceRequest();
      sr.setId("sr13");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID));

      // Should not throw, just return false since "nonExistentParam" doesn't exist
      assertThat(checkerWithInvalidParam.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("exception during property lookup is handled gracefully")
    void exceptionDuringPropertyLookup_handledGracefully() {
      var compartmentService =
          new PatientCompartmentService(Map.of("TestResource", List.of("subject")));
      var checkerWithMock = new CompartmentMembershipChecker(compartmentService);

      // Create a mock resource that throws when getNamedProperty is called
      Resource mockResource = mock(Resource.class);
      when(mockResource.fhirType()).thenReturn("TestResource");
      when(mockResource.getIdPart()).thenReturn("test1");
      when(mockResource.getNamedProperty(anyString()))
          .thenThrow(new RuntimeException("Test exception"));

      // Should handle the exception gracefully and return false
      assertThat(checkerWithMock.isInPatientCompartment(mockResource, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("Patient resource type but null ID -> NOT in compartment")
    void patientResourceTypeWithNullId_notInCompartment() {
      // Tests the && short-circuit at line 38 where resourceType is Patient but getIdPart is null
      var patient = new org.hl7.fhir.r4.model.Patient();
      // Don't set ID, so getIdPart() returns null

      assertThat(checker.isInPatientCompartment(patient, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("reference with just ID (no type prefix) -> NOT in compartment")
    void referenceWithJustId_notInCompartment() {
      // Tests extractIdFromReference with non-Patient/ prefix
      var sr = new ServiceRequest();
      sr.setId("sr14");
      sr.setSubject(new Reference(PATIENT_ID)); // Just ID, no "Patient/" prefix

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("full URL reference ending with Patient/ID -> IN compartment")
    void fullUrlReference_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr15");
      sr.setSubject(new Reference("http://example.com/fhir/Patient/" + PATIENT_ID));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("reference with trailing path after ID -> IN compartment")
    void referenceWithTrailingPath_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr16");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID + "/_history/1"));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("reference with query params after ID -> IN compartment")
    void referenceWithQueryParams_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr17");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID + "?_format=json"));

      assertThat(checker.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }
  }
}
