package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static reactor.test.StepVerifier.create;

import care.smith.fts.util.tca.DateShiftingRequest;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class FhirShiftedDatesProviderTest {
  @MockBean RedissonClient redisClient;
  @Mock RedissonReactiveClient redis;
  @Mock RBucketReactive<Object> bucket;
  FhirShiftedDatesProvider provider;

  @BeforeEach
  void setUp() {
    provider = new FhirShiftedDatesProvider(redisClient);
  }

  @Test
  void generateDateShiftT() {
    given(redisClient.reactive()).willReturn(redis);
    given(redis.getBucket(anyString())).willReturn(bucket);

    given(bucket.setIfAbsent(anyString())).willReturn(Mono.just(true));
    given(bucket.get()).willReturn(Mono.just("2"));
    var request = new DateShiftingRequest("1", Duration.ofDays(14));

    var expectedShiftedDate = Duration.ofMillis(2);

    create(provider.generateDateShift("1", request.dateShift()))
        .assertNext(shiftedDate -> assertThat(shiftedDate).isEqualTo(expectedShiftedDate))
        .verifyComplete();
  }

  @Test
  void getRandomDateShift() {
    for (int i = 0; i < 1000; i++) {
      assertThat(provider.getRandomDateShift(Duration.ofMillis(100))).isBetween(-100L, 100L);
    }
  }
}
