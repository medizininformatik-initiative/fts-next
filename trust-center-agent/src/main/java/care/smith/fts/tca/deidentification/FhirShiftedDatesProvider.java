package care.smith.fts.tca.deidentification;

import static java.lang.Long.parseLong;
import static java.time.Duration.ofMillis;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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
  public Mono<Map<String, Duration>> generateDateShift(Set<String> ids, Duration dateShiftBy) {
    RedissonReactiveClient redis = redisClient.reactive();
    return Flux.fromIterable(ids)
        .flatMap(
            id ->
                redis
                    .getBucket(withPrefix(id))
                    .setIfAbsent(String.valueOf(getRandomDateShift(dateShiftBy)))
                    .doOnError(e -> log.error("Set date shift: {}", e.getMessage()))
                    .switchIfEmpty(Mono.just(true)) // TODO check if we need this
                    .map(ret -> id))
        .doOnNext(id -> log.trace("generateDateShift for id: {}", id))
        .flatMap(
            id ->
                redis
                    .<String>getBucket(withPrefix(id))
                    .get()
                    .doOnError(e -> log.error("Receive date shift: {}", e.getMessage()))
                    .map(shift -> new Entry(id, ofMillis(parseLong(shift)))))
        .collectMap(Entry::id, Entry::shift);
  }

  private static String withPrefix(String id) {
    return "%s:%s".formatted(SHIFTED_DATE_PREFIX, id);
  }

  public long getRandomDateShift(Duration dateShiftBy) {
    return new Random().nextLong(-dateShiftBy.toMillis(), dateShiftBy.toMillis());
  }

  private record Entry(String id, Duration shift) {}
}
