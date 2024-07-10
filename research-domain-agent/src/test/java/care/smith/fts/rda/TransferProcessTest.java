package care.smith.fts.rda;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.DeidentificationProvider;
import care.smith.fts.rda.test.MockBundleSender;
import care.smith.fts.rda.test.MockDeidentificationProvider;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransferProcessTest {

  @Test
  void toStringContainsImplementationNames() {
    TransferProcess process =
        new TransferProcess(
            "test",
            new MockDeidentificationProvider()
                .create(
                    new DeidentificationProvider.Config(),
                    new MockDeidentificationProvider.Config(false)),
            new MockBundleSender()
                .create(new BundleSender.Config(), new MockBundleSender.Config(Set.of())));

    assertThat(process.toString())
        .contains("MockDeidentificationProvider")
        .contains("MockBundleSender");
  }
}
