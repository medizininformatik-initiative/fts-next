package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.DataSelector;
import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.cda.impl.MockBundleSender;
import care.smith.fts.cda.impl.MockCohortSelector;
import care.smith.fts.cda.impl.MockDataSelector;
import care.smith.fts.cda.impl.MockDeidentificationProvider;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransferProcessTest {
  @Test
  void toStringContainsImplementationNames() {
    TransferProcess process =
        new TransferProcess(
            new MockCohortSelector()
                .create(new CohortSelector.Config(), new MockCohortSelector.Config(List.of())),
            new MockDataSelector()
                .create(new DataSelector.Config(null, null), new MockDataSelector.Config()),
            new MockDeidentificationProvider()
                .create(
                    new DeidentificationProvider.Config(),
                    new MockDeidentificationProvider.Config(false)),
            new MockBundleSender()
                .create(new BundleSender.Config(), new MockBundleSender.Config(Set.of())));

    assertThat(process.toString())
        .contains("MockCohortSelector")
        .contains("MockDataSelector")
        .contains("MockDeidentificationProvider")
        .contains("MockBundleSender");
  }
}
