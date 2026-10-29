package care.smith.fts.api;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

public record ConsentedPatient(
    String identifier, String patientIdentifierSystem, ConsentedPolicies consentedPolicies) {

  public ConsentedPatient {
    requireNonNull(identifier, "Patient's identifier cannot be null");
    requireNonNull(patientIdentifierSystem, "Patient's patientIdentifierSystem cannot be null");
    requireNonNull(consentedPolicies, "Consented policies cannot be null");
  }

  public ConsentedPatient(String identifier, String patientIdentifierSystem) {
    this(identifier, patientIdentifierSystem, new ConsentedPolicies());
  }

  public Optional<Period> maxConsentedPeriod() {
    return consentedPolicies.maxConsentedPeriod();
  }

  @ToString
  @EqualsAndHashCode
  public static class ConsentedPolicies {

    @JsonSerialize(using = MultimapSerializer.class)
    @JsonDeserialize(using = MultimapDeserializer.class)
    private final SetMultimap<String, Period> policies = HashMultimap.create();

    public void put(String id, Period period) {
      policies.put(id, period);
    }

    public Boolean hasAllPolicies(Set<String> policiesToCheck) {
      return policies.keySet().containsAll(policiesToCheck);
    }

    /**
     * The period all policies are consented to: from the latest policy start to the earliest policy
     * end. Per policy, the earliest start and the latest end of its periods count. An open-ended
     * period outlasts any bounded one.
     *
     * @return the consented period, or empty if there are no policies or their periods do not
     *     overlap
     */
    public Optional<Period> maxConsentedPeriod() {
      if (policies.isEmpty()) {
        return Optional.empty();
      }
      var periodsPerPolicy = policies.asMap().values();
      var start =
          periodsPerPolicy.stream()
              .map(ConsentedPolicies::minStartOfPolicyPeriods)
              .max(TIME_LINE_ORDER)
              .orElseThrow();
      var end =
          periodsPerPolicy.stream()
              .map(ConsentedPolicies::maxEndOfPolicyPeriods)
              .min(OPEN_END_LAST)
              .orElseThrow();
      return Optional.of(Period.of(start, end.orElse(null)))
          .filter(ConsentedPolicies::endsAfterStart);
    }

    /**
     * Orders date-times by their instant on the timeline, ignoring zone and chronology. Unlike
     * {@link ZonedDateTime#compareTo}, the same instant in different zones compares as equal.
     */
    private static final Comparator<ChronoZonedDateTime<?>> TIME_LINE_ORDER =
        ChronoZonedDateTime.timeLineOrder();

    /**
     * Orders period ends by {@link #TIME_LINE_ORDER}, with an empty (open) end after every bounded
     * end, since an open-ended period lasts indefinitely.
     */
    private static final Comparator<Optional<ZonedDateTime>> OPEN_END_LAST =
        Comparator.comparing(end -> end.orElse(null), Comparator.nullsLast(TIME_LINE_ORDER));

    /**
     * @param col the periods of one policy, not empty
     * @return the earliest start of the periods
     */
    private static ZonedDateTime minStartOfPolicyPeriods(Collection<Period> col) {
      return col.stream().map(Period::start).min(TIME_LINE_ORDER).orElseThrow();
    }

    /**
     * @param col the periods of one policy, not empty
     * @return the latest end of the periods, or empty if any of them is open-ended
     */
    private static Optional<ZonedDateTime> maxEndOfPolicyPeriods(Collection<Period> col) {
      return col.stream().map(Period::end).max(OPEN_END_LAST).orElseThrow();
    }

    /**
     * A zero-length or inverted period is no consent, because there is no time to select data for.
     *
     * @param period the period to check
     * @return {@code true} if the period ends strictly after it starts or is open-ended
     */
    private static boolean endsAfterStart(Period period) {
      return period.end().map(period.start()::isBefore).orElse(true);
    }

    public Boolean hasPolicy(String policy) {
      return policies.keySet().contains(policy);
    }

    public Set<String> policyNames() {
      return policies.keySet();
    }

    public int numberOfPolicies() {
      return policies.keySet().size();
    }

    public Set<Period> getPeriods(String policy) {
      return policies.get(policy);
    }

    public void merge(ConsentedPolicies other) {
      other.policies.forEach(policies::put);
    }
  }

  static class MultimapSerializer extends ValueSerializer<Multimap<String, Period>> {
    @Override
    public void serialize(
        Multimap<String, Period> value, JsonGenerator gen, SerializationContext ctxt) {
      Map<String, Collection<Period>> map = value.asMap();
      gen.writePOJO(map);
    }
  }

  static class MultimapDeserializer extends ValueDeserializer<Multimap<String, Period>> {
    @Override
    public Multimap<String, Period> deserialize(JsonParser p, DeserializationContext ctxt) {
      Map<String, Collection<Period>> map =
          p.readValueAs(new TypeReference<Map<String, Collection<Period>>>() {});
      SetMultimap<String, Period> multimap = HashMultimap.create();
      map.forEach(multimap::putAll);
      return multimap;
    }
  }
}
