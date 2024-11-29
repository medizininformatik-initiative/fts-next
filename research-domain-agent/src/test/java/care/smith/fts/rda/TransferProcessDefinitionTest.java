package care.smith.fts.rda;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.test.MockBundleSender;
import care.smith.fts.rda.test.MockDeidentificator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransferProcessDefinitionTest {

  @Test
  void toStringContainsImplementationNames() {
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            new TransferProcessConfig(Map.of("a", "b"), Map.of("c", "d")),
            new MockDeidentificator()
                .create(new Deidentificator.Config(), new MockDeidentificator.Config(false)),
            new MockBundleSender()
                .create(new BundleSender.Config(), new MockBundleSender.Config(Set.of())));
    System.out.println(process);
    assertThat(process.toString()).contains("MockDeidentificator").contains("MockBundleSender");
  }
}
