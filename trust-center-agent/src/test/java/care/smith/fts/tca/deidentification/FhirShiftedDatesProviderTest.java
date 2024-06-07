package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import care.smith.fts.tca.deidentification.configuration.DateShiftingConfiguration;
import care.smith.fts.util.tca.DateShiftingRequest;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Slf4j
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class FhirShiftedDatesProviderTest {
  @Mock JedisPool jedisPool;
  @Mock Jedis jedis;
  @Autowired DateShiftingConfiguration configuration;

  FhirShiftedDatesProvider provider;

  @BeforeEach
  void setUp() {
    provider = new FhirShiftedDatesProvider(configuration, jedisPool);
  }

  @Test
  void generateShiftedDates() {
    given(jedisPool.getResource()).willReturn(jedis);
    given(jedis.set(anyString(), anyString(), any(SetParams.class))).willReturn("OK");
    given(jedis.get("shiftedDate:1")).willReturn("2");
    given(jedis.get("shiftedDate:2")).willReturn("4");
    given(jedis.get("shiftedDate:3")).willReturn("6");
    var request = new DateShiftingRequest();
    request.setIds(Set.of("1", "2", "3"));
    var shiftedDates = provider.generateShiftedDates(request);
    assertThat(shiftedDates.get("1")).isEqualTo(2);
    assertThat(shiftedDates.get("2")).isEqualTo(4);
    assertThat(shiftedDates.get("3")).isEqualTo(6);
  }

  @Test
  void getRandomLong() {
    for (int i = 0; i < 1000; i++) {
      assertThat(provider.getRandomLong(-100, 100)).isBetween(-100L, 100L);
    }
  }
}
