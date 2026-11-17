package care.smith.fts.cda.rest;

import static org.mockito.Mockito.when;

import care.smith.fts.cda.selfassessment.SelfAssessmentService;
import care.smith.fts.util.selfassessment.SelfAssessmentReport;
import care.smith.fts.util.selfassessment.Status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SelfAssessmentControllerTest {

  @Mock SelfAssessmentService service;

  @Test
  void getReturnsServiceReport() {
    var report = new SelfAssessmentReport("clinical-domain-agent", Status.UP, List.of(), List.of());
    when(service.assess()).thenReturn(Mono.just(report));

    var controller = new SelfAssessmentController(service);

    StepVerifier.create(controller.get()).expectNext(report).verifyComplete();
  }
}
