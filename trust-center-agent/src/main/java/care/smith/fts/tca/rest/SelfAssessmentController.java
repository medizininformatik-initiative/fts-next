package care.smith.fts.tca.rest;

import care.smith.fts.tca.selfassessment.SelfAssessmentService;
import care.smith.fts.util.selfassessment.SelfAssessmentReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/v2")
public class SelfAssessmentController {

  private final SelfAssessmentService service;

  public SelfAssessmentController(SelfAssessmentService service) {
    this.service = service;
  }

  @GetMapping("/self-assessment")
  @Operation(
      summary = "Agent self-assessment",
      description =
          "Reports configuration validity and live reachability of every external dependency "
              + "configured for this agent. Always returns 200 with status inside the body so "
              + "callers can branch on the response shape.",
      responses = {@ApiResponse(responseCode = "200", description = "Self-assessment report")})
  public Mono<SelfAssessmentReport> get() {
    return service.assess();
  }
}
