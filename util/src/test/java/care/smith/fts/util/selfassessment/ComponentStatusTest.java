package care.smith.fts.util.selfassessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComponentStatusTest {

  @Test
  void upHasUpStatusAndLatencyAndNoReason() {
    var c = ComponentStatus.up("gpas", "fhir", "http://gpas", 42L);
    assertThat(c.status()).isEqualTo(Status.UP);
    assertThat(c.latencyMs()).isEqualTo(42L);
    assertThat(c.reason()).isNull();
    assertThat(c.url()).isEqualTo("http://gpas");
  }

  @Test
  void downHasDownStatusAndReasonAndNoLatency() {
    var c = ComponentStatus.down("gpas", "fhir", "http://gpas", "refused");
    assertThat(c.status()).isEqualTo(Status.DOWN);
    assertThat(c.latencyMs()).isNull();
    assertThat(c.reason()).isEqualTo("refused");
  }

  @Test
  void degradedHasLatencyAndReason() {
    var c = ComponentStatus.degraded("gpas", "fhir", "http://gpas", 12L, "missing ops");
    assertThat(c.status()).isEqualTo(Status.DEGRADED);
    assertThat(c.latencyMs()).isEqualTo(12L);
    assertThat(c.reason()).isEqualTo("missing ops");
  }

  @Test
  void skippedHasNullUrlAndNullLatencyAndReason() {
    var c = ComponentStatus.skipped("gics", "fhir", "not configured");
    assertThat(c.status()).isEqualTo(Status.SKIPPED);
    assertThat(c.url()).isNull();
    assertThat(c.latencyMs()).isNull();
    assertThat(c.reason()).isEqualTo("not configured");
    assertThat(c.name()).isEqualTo("gics");
    assertThat(c.kind()).isEqualTo("fhir");
  }
}
