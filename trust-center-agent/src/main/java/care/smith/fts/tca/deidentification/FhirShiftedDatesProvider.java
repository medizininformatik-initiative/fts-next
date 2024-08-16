package care.smith.fts.tca.deidentification;

import static java.lang.Long.parseLong;
import static java.time.Duration.ofMillis;

import java.time.Duration;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FhirShiftedDatesProvider implements ShiftedDatesProvider {
  private static final String SHIFTED_DATE_PREFIX = "shiftedDate";
  private final RedissonClient redisClient;

  public FhirShiftedDatesProvider(RedissonClient redisClient) {
    this.redisClient = redisClient;
  }

  @Override
  public Mono<Duration> generateDateShift(String id, Duration dateShiftBy) {
    RedissonReactiveClient redis = redisClient.reactive();
    return redis
        .getBucket(withPrefix(id))
        .setIfAbsent(String.valueOf(getRandomDateShift(dateShiftBy)))
        .doOnError(e -> log.error("Unable to set date shift: {}", e.getMessage()))
        .switchIfEmpty(Mono.just(true)) // TODO check if we need this
        .map(ret -> id)
        .doOnNext(ignore -> log.trace("generateDateShift for id: {}", id))
        .flatMap(
            ignore ->
                redis
                    .<String>getBucket(withPrefix(id))
                    .get()
                    .doOnError(e -> log.error("Unable to receive date shift: {}", e.getMessage()))
                    .map(shift -> ofMillis(parseLong(shift))));
  }

  private static String withPrefix(String id) {
    return "%s:%s".formatted(SHIFTED_DATE_PREFIX, id);
  }

  public long getRandomDateShift(Duration dateShiftBy) {
    return new Random().nextLong(-dateShiftBy.toMillis(), dateShiftBy.toMillis());
  }
}
