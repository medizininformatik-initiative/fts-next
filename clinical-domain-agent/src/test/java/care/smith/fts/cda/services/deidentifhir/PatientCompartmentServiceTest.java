package care.smith.fts.cda.services.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PatientCompartmentServiceTest {

  private static final String PATIENT_ID = "patient123";

  private PatientCompartmentService service;

  @BeforeEach
  void setUp() {
    // ServiceRequest has params ["subject", "performer"] according to compartment definition
    // Organization has no params (never in compartment)
    // Appointment has "actor" which requires nested path: participant.actor
    // CareTeam has "participant" which requires nested path: participant.member
    // Coverage has "policy-holder" which maps to field "policyHolder"
    Map<String, List<String>> compartmentParams =
        Map.of(
            "ServiceRequest", List.of("subject", "performer"),
            "Organization", List.of(),
            "Observation", List.of("subject", "performer"),
            "Appointment", List.of("actor"),
            "CareTeam", List.of("participant"),
            "Coverage", List.of("policy-holder", "subscriber", "beneficiary"));
    service = new PatientCompartmentService(compartmentParams);
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

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("performer references patient -> IN compartment")
    void performerReferencesPatient_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr2");
      sr.setSubject(new Reference("Group/group1"));
      sr.addPerformer(new Reference("Patient/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("both subject and performer reference patient -> IN compartment")
    void bothReferencePatient_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr3");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID));
      sr.addPerformer(new Reference("Patient/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("neither subject nor performer references patient -> NOT in compartment")
    void neitherReferencesPatient_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr4");
      sr.setSubject(new Reference("Group/group1"));
      sr.addPerformer(new Reference("Organization/org1"));

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
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

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("subject references different patient -> NOT in compartment")
    void subjectReferencesDifferentPatient_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr6");
      sr.setSubject(new Reference("Patient/differentPatient"));

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("empty ServiceRequest -> NOT in compartment")
    void emptyServiceRequest_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr7");

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
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

      assertThat(service.isInPatientCompartment(org, PATIENT_ID)).isFalse();
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

      assertThat(service.isInPatientCompartment(patient, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("Patient resource is different patient -> NOT in compartment")
    void patientResourceIsDifferentPatient_notInCompartment() {
      var patient = new org.hl7.fhir.r4.model.Patient();
      patient.setId("differentPatient");

      assertThat(service.isInPatientCompartment(patient, PATIENT_ID)).isFalse();
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

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }
  }

  @Nested
  @DisplayName("Nested path resolution - Appointment (participant.actor)")
  class AppointmentNestedPathTests {

    @Test
    @DisplayName("participant.actor references patient -> IN compartment")
    void participantActorReferencesPatient_isInCompartment() {
      var appointment = new Appointment();
      appointment.setId("apt1");
      var participant = appointment.addParticipant();
      participant.setActor(new Reference("Patient/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(appointment, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("multiple participants, one references patient -> IN compartment")
    void multipleParticipants_oneReferencesPatient_isInCompartment() {
      var appointment = new Appointment();
      appointment.setId("apt2");
      appointment.addParticipant().setActor(new Reference("Practitioner/doc1"));
      appointment.addParticipant().setActor(new Reference("Patient/" + PATIENT_ID));
      appointment.addParticipant().setActor(new Reference("Location/loc1"));

      assertThat(service.isInPatientCompartment(appointment, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("no participant references patient -> NOT in compartment")
    void noParticipantReferencesPatient_notInCompartment() {
      var appointment = new Appointment();
      appointment.setId("apt3");
      appointment.addParticipant().setActor(new Reference("Practitioner/doc1"));
      appointment.addParticipant().setActor(new Reference("Location/loc1"));

      assertThat(service.isInPatientCompartment(appointment, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("empty appointment -> NOT in compartment")
    void emptyAppointment_notInCompartment() {
      var appointment = new Appointment();
      appointment.setId("apt4");

      assertThat(service.isInPatientCompartment(appointment, PATIENT_ID)).isFalse();
    }
  }

  @Nested
  @DisplayName("Nested path resolution - CareTeam (participant.member)")
  class CareTeamNestedPathTests {

    @Test
    @DisplayName("participant.member references patient -> IN compartment")
    void participantMemberReferencesPatient_isInCompartment() {
      var careTeam = new CareTeam();
      careTeam.setId("ct1");
      var participant = careTeam.addParticipant();
      participant.setMember(new Reference("Patient/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(careTeam, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("multiple participants, one member references patient -> IN compartment")
    void multipleParticipants_oneMemberReferencesPatient_isInCompartment() {
      var careTeam = new CareTeam();
      careTeam.setId("ct2");
      careTeam.addParticipant().setMember(new Reference("Practitioner/doc1"));
      careTeam.addParticipant().setMember(new Reference("Patient/" + PATIENT_ID));
      careTeam.addParticipant().setMember(new Reference("Organization/org1"));

      assertThat(service.isInPatientCompartment(careTeam, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("no participant member references patient -> NOT in compartment")
    void noParticipantMemberReferencesPatient_notInCompartment() {
      var careTeam = new CareTeam();
      careTeam.setId("ct3");
      careTeam.addParticipant().setMember(new Reference("Practitioner/doc1"));

      assertThat(service.isInPatientCompartment(careTeam, PATIENT_ID)).isFalse();
    }
  }

  @Nested
  @DisplayName("Simple mapping - Coverage (policy-holder -> policyHolder)")
  class CoverageSimpleMappingTests {

    @Test
    @DisplayName("policyHolder references patient -> IN compartment")
    void policyHolderReferencesPatient_isInCompartment() {
      var coverage = new Coverage();
      coverage.setId("cov1");
      coverage.setPolicyHolder(new Reference("Patient/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(coverage, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("subscriber references patient -> IN compartment")
    void subscriberReferencesPatient_isInCompartment() {
      var coverage = new Coverage();
      coverage.setId("cov2");
      coverage.setSubscriber(new Reference("Patient/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(coverage, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("beneficiary references patient -> IN compartment")
    void beneficiaryReferencesPatient_isInCompartment() {
      var coverage = new Coverage();
      coverage.setId("cov3");
      coverage.setBeneficiary(new Reference("Patient/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(coverage, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("no fields reference patient -> NOT in compartment")
    void noFieldsReferencePatient_notInCompartment() {
      var coverage = new Coverage();
      coverage.setId("cov4");
      coverage.setPolicyHolder(new Reference("RelatedPerson/rp1"));
      coverage.setSubscriber(new Reference("RelatedPerson/rp2"));
      coverage.setBeneficiary(new Reference("Patient/differentPatient"));

      assertThat(service.isInPatientCompartment(coverage, PATIENT_ID)).isFalse();
    }
  }

  @Nested
  @DisplayName("Edge cases for nested path resolution")
  class NestedPathEdgeCasesTests {

    @Test
    @DisplayName("resource in NESTED_PATHS but param not in inner map -> falls back to top-level")
    void resourceInNestedPathsButParamNotInMap_fallsBackToTopLevel() {
      // Appointment is in NESTED_PATHS with "actor" param, but we test with "patient" param
      // which is NOT in Appointment's nested paths map, triggering line 171 (paths == null)
      var compartmentParams = Map.of("Appointment", List.of("patient"));
      var checkerWithDifferentParam = new PatientCompartmentService(compartmentParams);

      var appointment = new Appointment();
      appointment.setId("apt-edge1");
      // "patient" param would fall back to top-level lookup, which won't find anything
      // because Appointment doesn't have a top-level "patient" field

      assertThat(checkerWithDifferentParam.isInPatientCompartment(appointment, PATIENT_ID))
          .isFalse();
    }

    @Test
    @DisplayName("participant exists but actor is null -> empty refs from traversePath")
    void participantExistsButActorNull_emptyRefs() {
      // Tests line 207: prop.getValues().isEmpty() at the final path segment
      var appointment = new Appointment();
      appointment.setId("apt-edge2");
      var participant = appointment.addParticipant();
      // participant exists but actor is not set (null)

      assertThat(service.isInPatientCompartment(appointment, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("deeply nested path with missing intermediate property")
    void deeplyNestedPathWithMissingIntermediate_emptyRefs() {
      // RequestGroup has path "action.participant.actor" - tests traversal through empty lists
      var compartmentParams = Map.of("RequestGroup", List.of("participant"));
      var checkerForRequestGroup = new PatientCompartmentService(compartmentParams);

      var requestGroup = new org.hl7.fhir.r4.model.RequestGroup();
      requestGroup.setId("rg1");
      // action list is empty, so traversePath returns empty at first segment

      assertThat(checkerForRequestGroup.isInPatientCompartment(requestGroup, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("exception during nested path traversal is handled gracefully")
    void exceptionDuringNestedPathTraversal_handledGracefully() {
      // Configure a checker that uses Appointment (which has nested paths)
      var compartmentParams = Map.of("Appointment", List.of("actor"));
      var checkerWithMock = new PatientCompartmentService(compartmentParams);

      // Mock resource that throws when traversing nested path
      Resource mockResource = mock(Resource.class);
      when(mockResource.fhirType()).thenReturn("Appointment");
      when(mockResource.getIdPart()).thenReturn("mock-apt");
      when(mockResource.getNamedProperty("participant"))
          .thenThrow(new RuntimeException("Simulated traversal error"));

      // Should handle exception gracefully and return false
      assertThat(checkerWithMock.isInPatientCompartment(mockResource, PATIENT_ID)).isFalse();
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

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("empty reference -> NOT in compartment")
    void emptyReference_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr10");
      sr.setSubject(new Reference());

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("reference with null value -> NOT in compartment")
    void referenceWithNullValue_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr11");
      var ref = new Reference();
      ref.setDisplay("Some display"); // Has display but no reference value
      sr.setSubject(ref);

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("reference to non-Patient resource type -> NOT in compartment")
    void referenceToNonPatient_notInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr12");
      sr.setSubject(new Reference("RelatedPerson/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("invalid param name does not cause exception")
    void invalidParamName_handledGracefully() {
      var compartmentParams = Map.of("ServiceRequest", List.of("nonExistentParam"));
      var checkerWithInvalidParam = new PatientCompartmentService(compartmentParams);

      var sr = new ServiceRequest();
      sr.setId("sr13");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID));

      // Should not throw, just return false since "nonExistentParam" doesn't exist
      assertThat(checkerWithInvalidParam.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("exception during property lookup is handled gracefully")
    void exceptionDuringPropertyLookup_handledGracefully() {
      var compartmentParams = Map.of("TestResource", List.of("subject"));
      var checkerWithMock = new PatientCompartmentService(compartmentParams);

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

      assertThat(service.isInPatientCompartment(patient, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("reference with just ID (no type prefix) -> NOT in compartment")
    void referenceWithJustId_notInCompartment() {
      // Tests extractIdFromReference with non-Patient/ prefix
      var sr = new ServiceRequest();
      sr.setId("sr14");
      sr.setSubject(new Reference(PATIENT_ID)); // Just ID, no "Patient/" prefix

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isFalse();
    }

    @Test
    @DisplayName("full URL reference ending with Patient/ID -> IN compartment")
    void fullUrlReference_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr15");
      sr.setSubject(new Reference("http://example.com/fhir/Patient/" + PATIENT_ID));

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("reference with trailing path after ID -> IN compartment")
    void referenceWithTrailingPath_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr16");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID + "/_history/1"));

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }

    @Test
    @DisplayName("reference with query params after ID -> IN compartment")
    void referenceWithQueryParams_isInCompartment() {
      var sr = new ServiceRequest();
      sr.setId("sr17");
      sr.setSubject(new Reference("Patient/" + PATIENT_ID + "?_format=json"));

      assertThat(service.isInPatientCompartment(sr, PATIENT_ID)).isTrue();
    }
  }
}
