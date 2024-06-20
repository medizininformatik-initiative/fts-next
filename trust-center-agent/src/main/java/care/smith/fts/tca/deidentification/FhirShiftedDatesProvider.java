package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.ShiftedDates;
import java.time.Duration;
import java.util.Set;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.springframework.stereotype.Component;
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
  public Mono<ShiftedDates> generateDateShift(Set<String> ids, Duration dateShiftBy) {

    var shiftByMillis = dateShiftBy.toMillis();
    ShiftedDates shiftedDates = new ShiftedDates();
    try (Jedis jedis = jedisPool.getResource()) {
      ids.forEach(
          id -> {
            var kid = "shiftedDate:" + id;
            jedis.set(
                kid,
                String.valueOf(getRandomLong(-shiftByMillis, shiftByMillis)),
                new SetParams().nx());
            var s = Long.parseLong(jedis.get(kid));
            shiftedDates.put(id, Duration.ofMillis(s));
          });
    }
    return Mono.just(shiftedDates);
  }

  public long getRandomLong(long lower, long upper) {
    return new RandomDataGenerator().nextLong(lower, upper);
  }
}
