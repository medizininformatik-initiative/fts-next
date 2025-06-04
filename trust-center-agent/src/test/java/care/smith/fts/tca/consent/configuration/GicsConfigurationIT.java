package care.smith.fts.tca.consent.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.WebClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class GicsConfigurationIT {
  @Autowired private GicsConfiguration gicsConfiguration;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  /*
   * The page size is set to 200 in test/resources/application.yaml
   * */
  @Test
  void getPageSize() {
    assertThat(gicsConfiguration.pageSize()).isEqualTo(200);
  }

  @Test
  void getDefaultPageSizeIfPageSizeIsNull() {
    var localGicsConfiguration = new GicsConfiguration();
    localGicsConfiguration.setPageSize(null);

    assertThat(localGicsConfiguration.pageSize()).isEqualTo(50);
  }

  @Test
  void gicsClientNotNull(@Autowired WebClientFactory factory) {
    assertThat(gicsConfiguration.gicsClient(factory)).isNotNull();
  }
}
