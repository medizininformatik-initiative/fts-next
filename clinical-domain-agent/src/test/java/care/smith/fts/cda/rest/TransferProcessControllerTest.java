package care.smith.fts.cda.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransferProcessControllerTest {
  @Autowired TransferProcessController api;

  @Test
  void startExistingProjectSucceeds() {
    assertThat(api.start("example")).allMatch(Boolean.TRUE::equals);
  }
}
