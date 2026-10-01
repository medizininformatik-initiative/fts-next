package care.smith.fts.cda.selfassessment;

import static care.smith.fts.util.selfassessment.Probes.DEFAULT_TIMEOUT;
import static care.smith.fts.util.selfassessment.Probes.probeReachable;
import static java.util.Objects.requireNonNullElse;

import care.smith.fts.cda.TransferProcessConfig;
import care.smith.fts.cda.TransferProcessDefinition;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.selfassessment.ComponentStatus;
import care.smith.fts.util.selfassessment.DownstreamExtractor;
import care.smith.fts.util.selfassessment.ProjectStatus;
import care.smith.fts.util.selfassessment.SelfAssessmentReport;
import care.smith.fts.util.selfassessment.StatusAggregator;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SelfAssessmentService {

  private static final String AGENT = "clinical-domain-agent";

  private final List<TransferProcessDefinition> projects;
  private final WebClientFactory webClientFactory;
  private final Duration timeout;
  private final int concurrency;

  public SelfAssessmentService(
      List<TransferProcessDefinition> projects,
      WebClientFactory webClientFactory,
      @Value("${selfassessment.timeout:PT3S}") Duration timeout,
      @Value("${selfassessment.concurrency:8}") int concurrency) {
    this.projects = projects;
    this.webClientFactory = webClientFactory;
    this.timeout = requireNonNullElse(timeout, DEFAULT_TIMEOUT);
    this.concurrency = concurrency;
  }

  public Mono<SelfAssessmentReport> assess() {
    return Flux.fromIterable(projects)
        .flatMap(this::assessProject, concurrency)
        .collectList()
        .map(
            list ->
                new SelfAssessmentReport(
                    AGENT,
                    StatusAggregator.worstOf(list.stream().map(ProjectStatus::status)),
                    List.of(),
                    list));
  }

  private Mono<ProjectStatus> assessProject(TransferProcessDefinition def) {
    var downstreams = DownstreamExtractor.extract(toMap(def.rawConfig()));
    if (downstreams.isEmpty()) {
      return Mono.just(
          new ProjectStatus(
              def.project(), true, care.smith.fts.util.selfassessment.Status.UP, List.of()));
    }
    return Flux.fromIterable(downstreams)
        .flatMap(d -> probeDownstream(d.label(), d.baseUrl()), concurrency)
        .collectList()
        .map(
            comps ->
                new ProjectStatus(
                    def.project(),
                    true,
                    StatusAggregator.worstOf(comps.stream().map(ComponentStatus::status)),
                    comps));
  }

  private Mono<ComponentStatus> probeDownstream(String label, String baseUrl) {
    var client = webClientFactory.create(new HttpClientConfig(baseUrl));
    return probeReachable(client, label, "http", baseUrl, timeout);
  }

  private static java.util.Map<String, Object> toMap(TransferProcessConfig c) {
    var m = new LinkedHashMap<String, Object>();
    m.put("cohortSelector", c.cohortSelector());
    m.put("dataSelector", c.dataSelector());
    m.put("deidentificator", c.deidentificator());
    m.put("bundleSender", c.bundleSender());
    return m;
  }
}
