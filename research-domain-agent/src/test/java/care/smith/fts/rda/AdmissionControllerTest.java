package care.smith.fts.rda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.rda.AdmissionController.AdmitResult;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the three-tier admission's tier-1 (global hard cap) and tier-2 (elastic
 * per-project fair share). No Reactor involved.
 */
class AdmissionControllerTest {

  @Test
  void loneProjectFillsWholeBuffer() {
    var ac = new AdmissionController(4);

    for (int i = 0; i < 4; i++) {
      assertThat(ac.admit("A")).isEqualTo(AdmitResult.ACCEPT);
    }
    // 5th over the global cap
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.REJECT);
    assertThat(ac.usage("A")).isEqualTo(4);
    assertInvariant(ac);
  }

  @Test
  void secondActiveProjectHalvesFairShare() {
    var ac = new AdmissionController(4);

    // A grabs 2 (its fair share once B becomes active is 2)
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.ACCEPT);
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.ACCEPT);
    // B becomes active: with 2 active, fairShare = ceil(4/2) = 2
    assertThat(ac.admit("B")).isEqualTo(AdmitResult.ACCEPT);
    assertThat(ac.admit("B")).isEqualTo(AdmitResult.ACCEPT);
    // both at fair share now -> further admits rejected (over share)
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.REJECT);
    assertThat(ac.admit("B")).isEqualTo(AdmitResult.REJECT);
    assertInvariant(ac);
  }

  @Test
  void projectOverShareIsRejectedWhileOtherIsUnder() {
    var ac = new AdmissionController(4);

    // A fills the whole buffer while alone
    for (int i = 0; i < 4; i++) {
      assertThat(ac.admit("A")).isEqualTo(AdmitResult.ACCEPT);
    }
    // B arrives: active becomes 2, fairShare = 2. A is at 4 (over), but B is under.
    // B cannot get in because the global cap is full (A holds all permits).
    assertThat(ac.admit("B")).isEqualTo(AdmitResult.REJECT);
    // A is over its new share of 2, so A is also rejected.
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.REJECT);
    assertInvariant(ac);
  }

  @Test
  void releasingFreesPermitsToUnderServedProject() {
    var ac = new AdmissionController(4);

    for (int i = 0; i < 4; i++) {
      ac.admit("A");
    }
    // A drains two -> two global permits freed
    ac.release("A");
    ac.release("A");
    assertThat(ac.usage("A")).isEqualTo(2);

    // B now arrives, active = 2, fairShare = 2, B under share, global permits available
    assertThat(ac.admit("B")).isEqualTo(AdmitResult.ACCEPT);
    assertThat(ac.admit("B")).isEqualTo(AdmitResult.ACCEPT);
    // both at share
    assertThat(ac.admit("B")).isEqualTo(AdmitResult.REJECT);
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.REJECT);
    assertInvariant(ac);
  }

  @Test
  void projectDrainingToZeroLetsOtherReclaimWholeBuffer() {
    var ac = new AdmissionController(4);

    ac.admit("A");
    ac.admit("A");
    ac.admit("B");
    ac.admit("B");

    // B drains fully to 0
    ac.release("B");
    ac.release("B");
    assertThat(ac.usage("B")).isEqualTo(0);

    // A is now the lone active project: fairShare = 4, can reclaim the freed permits
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.ACCEPT);
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.ACCEPT);
    assertThat(ac.usage("A")).isEqualTo(4);
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.REJECT);
    assertInvariant(ac);
  }

  @Test
  void invariantHoldsAcrossInterleavedAdmitRelease() {
    var ac = new AdmissionController(8);

    ac.admit("A");
    ac.admit("B");
    ac.admit("A");
    ac.admit("C");
    ac.release("A");
    ac.admit("B");
    ac.admit("C");
    ac.release("C");
    ac.admit("A");

    assertInvariant(ac);
  }

  @Test
  void overShareRejectedWhileGlobalPermitsRemain() {
    // Buffer 4, two active projects -> fair share 2. A is at its share but a global permit is still
    // free, so the rejection is tier-2 (over share), not tier-1 (global cap). The permit A briefly
    // acquired must be handed back.
    var ac = new AdmissionController(4);
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.ACCEPT);
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.ACCEPT);
    assertThat(ac.admit("B")).isEqualTo(AdmitResult.ACCEPT);

    int before = ac.permitsInUse();
    // A is at fair share (2) with one global permit still free -> tier-2 reject, permit returned.
    assertThat(ac.admit("A")).isEqualTo(AdmitResult.REJECT);
    assertThat(ac.permitsInUse()).isEqualTo(before);
    assertInvariant(ac);
  }

  @Test
  void releasingMoreThanAdmittedThrows() {
    // Admit once then release twice: the first release removes the key, so the second finds no
    // matching admit (usage.get -> null) and must fail rather than drive usage negative.
    var ac = new AdmissionController(4);
    ac.admit("A");
    ac.release("A");

    assertThatThrownBy(() -> ac.release("A"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("release without matching admit");
  }

  @Test
  void releasingNeverAdmittedProjectThrows() {
    var ac = new AdmissionController(4);
    assertThatThrownBy(() -> ac.release("never-admitted"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("release without matching admit");
  }

  private static void assertInvariant(AdmissionController ac) {
    assertThat(ac.permitsInUse()).isEqualTo(ac.totalUsage());
  }
}
