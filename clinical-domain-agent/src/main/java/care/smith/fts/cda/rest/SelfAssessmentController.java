package care.smith.fts.cda.rest;

import care.smith.fts.cda.selfassessment.SelfAssessmentService;
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
          "Reports configuration validity per loaded project and live reachability of every "
              + "configured downstream service. Always returns 200 with status inside the body.",
      responses = {@ApiResponse(responseCode = "200", description = "Self-assessment report")})
  public Mono<SelfAssessmentReport> get() {
    return service.assess();
  }
}
