package care.smith.fts.util.selfassessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SelfAssessmentReportTest {

  @Test
  void exposesComponentsAndProjects() {
    var comp = ComponentStatus.up("redis", "redis", "redis://x", 5L);
    var proj = new ProjectStatus("p1", true, Status.UP, List.of());
    var r = new SelfAssessmentReport("agent", Status.UP, List.of(comp), List.of(proj));
    assertThat(r.agent()).isEqualTo("agent");
    assertThat(r.overall()).isEqualTo(Status.UP);
    assertThat(r.components()).containsExactly(comp);
    assertThat(r.projects()).containsExactly(proj);
  }
}
