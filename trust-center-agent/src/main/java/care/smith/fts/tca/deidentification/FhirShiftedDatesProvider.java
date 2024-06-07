package care.smith.fts.tca.deidentification;

import care.smith.fts.tca.deidentification.configuration.DateShiftingConfiguration;
import care.smith.fts.util.tca.DateShiftingRequest;
import care.smith.fts.util.tca.ShiftedDates;
import org.apache.commons.math3.random.RandomDataGenerator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

public class FhirShiftedDatesProvider implements ShiftedDatesProvider {
  private final DateShiftingConfiguration dateShiftingConfiguration;
  private final JedisPool jedisPool;

  public FhirShiftedDatesProvider(
      DateShiftingConfiguration dateShiftingConfiguration, JedisPool jedisPool) {
    this.dateShiftingConfiguration = dateShiftingConfiguration;
    this.jedisPool = jedisPool;
  }

  @Override
  public synchronized ShiftedDates generateShiftedDates(DateShiftingRequest dateShiftingRequest) {

    var shiftBy = dateShiftingConfiguration.getShiftByMillis();
    var ids = dateShiftingRequest.getIds();
    ShiftedDates shiftedDates = new ShiftedDates();
    try (Jedis jedis = jedisPool.getResource()) {
      ids.forEach(
          id -> {
            var kid = "shiftedDate:" + id;
            jedis.set(kid, String.valueOf(getRandomLong(-shiftBy, shiftBy)), new SetParams().nx());
            var s = Long.valueOf(jedis.get(kid));
            shiftedDates.put(id, s);
          });
    }
    return shiftedDates;
  }

  public long getRandomLong(long lower, long upper) {
    return new RandomDataGenerator().nextLong(lower, upper);
  }
}
