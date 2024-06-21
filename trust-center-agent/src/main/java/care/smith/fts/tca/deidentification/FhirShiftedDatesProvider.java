package care.smith.fts.tca.deidentification;

import static java.lang.Long.parseLong;
import static java.time.Duration.ofMillis;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Component
public class FhirShiftedDatesProvider implements ShiftedDatesProvider {
  private final JedisPool jedisPool;

  public FhirShiftedDatesProvider(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public Mono<Map<String, Duration>> generateDateShift(Set<String> ids, Duration dateShiftBy) {
    var shiftByMillis = dateShiftBy.toMillis();
    try (Jedis jedis = jedisPool.getResource()) {
      return Flux.fromStream(ids.stream().map(id -> "shiftedDate:" + id))
          .map(id -> generateIfNotExists(id, jedis, shiftByMillis))
          .map(id -> new Entry(id, ofMillis(parseLong(jedis.get(id)))))
          .collectMap(Entry::id, Entry::shift);
    }
  }

  private String generateIfNotExists(String id, Jedis jedis, long shiftByMillis) {
    var timeShift = getRandomLong(-shiftByMillis, shiftByMillis);
    jedis.set(id, String.valueOf(timeShift), new SetParams().nx());
    return id;
  }

  public long getRandomLong(long lower, long upper) {
    return new RandomDataGenerator().nextLong(lower, upper);
  }

  private record Entry(String id, Duration shift) {}
}
