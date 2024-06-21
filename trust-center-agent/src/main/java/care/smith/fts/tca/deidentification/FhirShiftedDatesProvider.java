package care.smith.fts.tca.deidentification;

import static java.lang.Long.parseLong;
import static java.time.Duration.ofMillis;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Component
public class FhirShiftedDatesProvider implements ShiftedDatesProvider {
  private static final String SHIFTED_DATE_PREFIX = "shiftedDate";
  private final JedisPool jedisPool;

  public FhirShiftedDatesProvider(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public Mono<Map<String, Duration>> generateDateShift(Set<String> ids, Duration dateShiftBy) {
    var shiftByMillis = dateShiftBy.toMillis();
    try (Jedis jedis = jedisPool.getResource()) {
      return Flux.fromIterable(ids)
          .map(id -> generateIfNotExists(id, jedis, shiftByMillis))
          .map(id -> new Entry(id, ofMillis(parseLong(jedis.get(withPrefix(id))))))
          .collectMap(Entry::id, Entry::shift);
    }
  }

  private String generateIfNotExists(String id, Jedis jedis, long shiftByMillis) {
    var timeShift = getRandomLong(-shiftByMillis, shiftByMillis);
    jedis.set((withPrefix(id)), String.valueOf(timeShift), new SetParams().nx());
    return id;
  }

  private static String withPrefix(String id) {
    return "%s:%s".formatted(SHIFTED_DATE_PREFIX, id);
  }

  public long getRandomLong(long lower, long upper) {
    return new Random().nextLong(lower, upper);
  }

  private record Entry(String id, Duration shift) {}
}
