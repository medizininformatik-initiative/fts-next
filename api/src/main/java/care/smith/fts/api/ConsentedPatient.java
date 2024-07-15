package care.smith.fts.api;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.ToString;

public record ConsentedPatient(String id, ConsentedPolicies consentedPolicies) {

  public ConsentedPatient {
    requireNonNull(id, "Patient's id cannot be null");
    requireNonNull(consentedPolicies, "Consented policies cannot be null");
  }

  public ConsentedPatient(String id) {
    this(id, new ConsentedPolicies());
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

    public Optional<Period> maxConsentedPeriod() {
      Optional<ZonedDateTime> start =
          policies.asMap().values().stream()
              .map(ConsentedPolicies::minStartOfPolicyPeriods)
              .max(ChronoZonedDateTime.timeLineOrder());
      Optional<ZonedDateTime> end =
          policies.asMap().values().stream()
              .map(ConsentedPolicies::maxEndOfPolicyPeriods)
              .min(ChronoZonedDateTime.timeLineOrder());
      return start.flatMap(s -> end.filter(e -> s.compareTo(e) < 0).map(e -> new Period(s, e)));
    }

    private static ZonedDateTime minStartOfPolicyPeriods(Collection<Period> col) {
      return col.stream().map(Period::start).min(ChronoZonedDateTime.timeLineOrder()).get();
    }

    private static ZonedDateTime maxEndOfPolicyPeriods(Collection<Period> col) {
      return col.stream().map(Period::end).max(ChronoZonedDateTime.timeLineOrder()).get();
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

  static class MultimapSerializer extends JsonSerializer<Multimap<String, Period>> {
    @Override
    public void serialize(
        Multimap<String, Period> value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      Map<String, Collection<Period>> map = value.asMap();
      gen.writeObject(map);
    }
  }

  static class MultimapDeserializer extends JsonDeserializer<Multimap<String, Period>> {
    @Override
    public Multimap<String, Period> deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      Map<String, Collection<Period>> map =
          p.readValueAs(new TypeReference<Map<String, Collection<Period>>>() {});
      SetMultimap<String, Period> multimap = HashMultimap.create();
      map.forEach(multimap::putAll);
      return multimap;
    }
  }
}
