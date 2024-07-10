package care.smith.fts.rda;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.test.MockBundleSender;
import care.smith.fts.rda.test.MockDeidentificator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransferProcessTest {

  @Test
  void toStringContainsImplementationNames() {
    TransferProcess process =
        new TransferProcess(
            "test",
            new MockDeidentificator()
                .create(new Deidentificator.Config(), new MockDeidentificator.Config(false)),
            new MockBundleSender()
                .create(new BundleSender.Config(), new MockBundleSender.Config(Set.of())));

    assertThat(process.toString()).contains("MockDeidentificator").contains("MockBundleSender");
  }
}
