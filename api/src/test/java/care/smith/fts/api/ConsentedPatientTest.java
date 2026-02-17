package care.smith.fts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class ConsentedPatientTest {

  @Test
  void nullPatientIdentifierSystemThrows() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ConsentedPatient("patient-122522", null))
        .withMessageContaining("patientIdentifierSystem");
  }

  @Test
  void emptyConsentYieldsEmptyMaxPeriod() {
    ConsentedPatient patient = new ConsentedPatient("patient-122522", "http://fts.smith.care");
    assertThat(patient.maxConsentedPeriod()).isEmpty();
  }

  @Test
  void put() {
    ConsentedPatient.ConsentedPolicies consentedPolicies = new ConsentedPatient.ConsentedPolicies();
    Period period = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-03T00:00:00+00:00");
    consentedPolicies.put("a", period);
    assertThat(consentedPolicies.hasPolicy("a")).isTrue();
    assertThat(consentedPolicies.getPeriods("a")).containsExactly(period);
  }

  @Test
  void hasAll() {
    ConsentedPatient.ConsentedPolicies consentedPolicies = new ConsentedPatient.ConsentedPolicies();
    Period period = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-03T00:00:00+00:00");
    consentedPolicies.put("a", period);
    consentedPolicies.put("b", period);
    assertThat(consentedPolicies.hasAllPolicies(Set.of())).isTrue();
    assertThat(consentedPolicies.hasAllPolicies(Set.of("a"))).isTrue();
    assertThat(consentedPolicies.hasAllPolicies(Set.of("b"))).isTrue();
    assertThat(consentedPolicies.hasAllPolicies(Set.of("a", "b"))).isTrue();
    assertThat(consentedPolicies.hasAllPolicies(Set.of("a", "b", "c"))).isFalse();
  }

  @Test
  void policyNames() {
    ConsentedPatient.ConsentedPolicies consentedPolicies = new ConsentedPatient.ConsentedPolicies();
    Period period = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-03T00:00:00+00:00");
    consentedPolicies.put("a", period);
    consentedPolicies.put("b", period);
    assertThat(consentedPolicies.policyNames()).isEqualTo(Set.of("a", "b"));
  }

  @Test
  void reasonableToString() {
    ConsentedPatient.ConsentedPolicies consentedPolicies = new ConsentedPatient.ConsentedPolicies();
    Period period = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-03T00:00:00+00:00");
    consentedPolicies.put("a", period);
    assertThat(consentedPolicies.toString()).contains("a").contains("1234-03-01T00:00");
  }

  @Test
  void mergeConsentedPolicies() {
    ConsentedPatient.ConsentedPolicies consentedPolicies1 =
        new ConsentedPatient.ConsentedPolicies();
    Period period1 = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-02T00:00:00+00:00");
    consentedPolicies1.put("a", period1);
    ConsentedPatient.ConsentedPolicies consentedPolicies2 =
        new ConsentedPatient.ConsentedPolicies();
    Period period2 = Period.parse("1234-04-01T00:00:00+00:00", "1234-04-02T00:00:00+00:00");
    consentedPolicies2.put("a", period2);
    consentedPolicies1.merge(consentedPolicies2);
    assertThat(consentedPolicies1.numberOfPolicies()).isEqualTo(1);
    assertThat(consentedPolicies1.hasPolicy("a")).isTrue();
  }

  @Test
  void getMaxPermittedPeriodWithOnePolicy() {
    ConsentedPatient.ConsentedPolicies consentedPolicies = new ConsentedPatient.ConsentedPolicies();
    Period period1 = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-03T00:00:00+00:00");
    Period period2 = Period.parse("1234-03-05T00:00:00+00:00", "1234-03-06T00:00:00+00:00");
    consentedPolicies.put("a", period1);
    consentedPolicies.put("a", period2);
    assertThat(consentedPolicies.maxConsentedPeriod())
        .isEqualTo(
            Optional.of(Period.parse("1234-03-01T00:00:00+00:00", "1234-03-06T00:00:00+00:00")));
  }

  @Test
  void getMaxPermittedPeriodWithTwoPoliciesWithNonOverlappingPeriods() {
    ConsentedPatient.ConsentedPolicies consentedPolicies = new ConsentedPatient.ConsentedPolicies();
    Period period1 = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-03T00:00:00+00:00");
    Period period2 = Period.parse("1234-03-05T00:00:00+00:00", "1234-03-06T00:00:00+00:00");
    consentedPolicies.put("a", period1);
    consentedPolicies.put("b", period2);
    assertThat(consentedPolicies.maxConsentedPeriod()).isEqualTo(Optional.empty());
  }

  @Test
  void getMaxPermittedPeriodWithTwoPoliciesWithOverlappingPeriods() {
    ConsentedPatient.ConsentedPolicies consentedPolicies = new ConsentedPatient.ConsentedPolicies();
    Period periodA1 = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-03T00:00:00+00:00");
    Period periodA2 = Period.parse("1234-03-05T00:00:00+00:00", "1234-03-06T00:00:00+00:00");
    consentedPolicies.put("a", periodA1);
    consentedPolicies.put("a", periodA2);
    Period periodB1 = Period.parse("1234-03-02T00:00:00+00:00", "1234-03-06T00:00:00+00:00");
    Period periodB2 = Period.parse("1234-03-08T00:00:00+00:00", "1234-03-12T00:00:00+00:00");
    consentedPolicies.put("b", periodB1);
    consentedPolicies.put("b", periodB2);

    assertThat(consentedPolicies.maxConsentedPeriod())
        .isEqualTo(
            Optional.of(Period.parse("1234-03-02T00:00:00+00:00", "1234-03-06T00:00:00+00:00")));
  }

  @Test
  void deAndSerializationUsingObjectMapper() throws JsonProcessingException {
    ConsentedPatient.ConsentedPolicies consentedPolicies = new ConsentedPatient.ConsentedPolicies();
    ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    Period period = Period.parse("1234-03-01T00:00:00+00:00", "1234-03-03T00:00:00+00:00");
    consentedPolicies.put("a", period);
    ConsentedPatient consentedPatient =
        new ConsentedPatient("patient", "http://fts.smith.care", consentedPolicies);

    String des = om.writeValueAsString(consentedPatient);
    ConsentedPatient ser = om.readValue(des, ConsentedPatient.class);
    assertThat(ser).isEqualTo(consentedPatient);
  }
}
