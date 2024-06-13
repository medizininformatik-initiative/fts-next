package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.DataSelector;
import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.cda.test.MockBundleSender;
import care.smith.fts.cda.test.MockCohortSelector;
import care.smith.fts.cda.test.MockDataSelector;
import care.smith.fts.cda.test.MockDeidentificationProvider;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransferProcessTest {
  @Test
  void toStringContainsImplementationNames() {
    TransferProcess<Bundle> process =
        new TransferProcess<>(
            "test",
            new MockCohortSelector()
                .create(new CohortSelector.Config(), new MockCohortSelector.Config("some")),
            new MockDataSelector()
                .create(new DataSelector.Config(false, null), new MockDataSelector.Config()),
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
