package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.test.MockBundleSender;
import care.smith.fts.cda.test.MockCohortSelector;
import care.smith.fts.cda.test.MockDataSelector;
import care.smith.fts.cda.test.MockDeidentificator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransferProcessDefinitionTest {
  @Test
  void toStringContainsImplementationNames() {
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            new TransferProcessConfig(null, null, null, null),
            new MockCohortSelector()
                .create(new CohortSelector.Config(), new MockCohortSelector.Config("some")),
            new MockDataSelector()
                .create(new DataSelector.Config(false, null), new MockDataSelector.Config()),
            new MockDeidentificator()
                .create(new Deidentificator.Config(), new MockDeidentificator.Config(false)),
            new MockBundleSender()
                .create(new BundleSender.Config(), new MockBundleSender.Config(Set.of())));

    assertThat(process.toString())
        .contains("MockCohortSelector")
        .contains("MockDataSelector")
        .contains("MockDeidentificator")
        .contains("MockBundleSender");
  }
}
