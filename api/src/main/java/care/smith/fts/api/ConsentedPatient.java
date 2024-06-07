package care.smith.fts.api;

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

public record ConsentedPatient(String id, ConsentedPolicies consentedPolicies) {

  public Optional<Period> maxConsentedPeriod() {
    return consentedPolicies.maxConsentedPeriod();
  }

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
      if (start.isPresent() && end.isPresent() && start.get().compareTo(end.get()) < 0) {
        return Optional.of(new Period(start.get(), end.get()));
      } else {
        return Optional.empty();
      }
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

    @Override
    public String toString() {
      return "ConsentedPolicies{" + "policies=" + policies + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ConsentedPolicies that = (ConsentedPolicies) o;
      return Objects.equals(policies, that.policies);
    }

    @Override
    public int hashCode() {
      return Objects.hash(policies);
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
