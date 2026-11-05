package care.smith.fts.util.selfassessment;

import java.util.List;

public record ProjectStatus(
    String name, boolean valid, Status status, List<ComponentStatus> downstream) {}
