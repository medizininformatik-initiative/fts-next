package care.smith.fts.util.selfassessment;

import java.util.List;

public record SelfAssessmentReport(
    String agent, Status overall, List<ComponentStatus> components, List<ProjectStatus> projects) {}
