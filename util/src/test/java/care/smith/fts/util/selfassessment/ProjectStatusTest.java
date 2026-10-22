package care.smith.fts.util.selfassessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectStatusTest {

  @Test
  void exposesConstructorComponents() {
    var comp = ComponentStatus.up("c", "http", "http://x", 1L);
    var ps = new ProjectStatus("project-1", true, Status.UP, List.of(comp));
    assertThat(ps.name()).isEqualTo("project-1");
    assertThat(ps.valid()).isTrue();
    assertThat(ps.status()).isEqualTo(Status.UP);
    assertThat(ps.downstream()).containsExactly(comp);
  }

  @Test
  void supportsInvalidProject() {
    var ps = new ProjectStatus("broken", false, Status.DOWN, List.of());
    assertThat(ps.valid()).isFalse();
    assertThat(ps.downstream()).isEmpty();
  }
}
