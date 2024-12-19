package care.smith.fts.rda.rest;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.HeaderTypes.X_PROGRESS;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static care.smith.fts.util.error.ErrorResponseUtil.notFound;
import static com.google.common.base.Predicates.and;
import static java.util.function.Predicate.not;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcessConfig;
import care.smith.fts.rda.TransferProcessDefinition;
import care.smith.fts.rda.TransferProcessRunner;
import care.smith.fts.rda.TransferProcessRunner.Status;
import care.smith.fts.util.error.ErrorResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v2")
@Validated
public class TransferProcessController {

  private final TransferProcessRunner processRunner;
  private final List<TransferProcessDefinition> processes;

  public TransferProcessController(
      TransferProcessRunner runner, List<TransferProcessDefinition> processes) {
    this.processRunner = runner;
    this.processes = processes;
  }

  @PostMapping(
      value = "/process/{project:[\\w-]+}/patient",
      consumes = APPLICATION_FHIR_JSON_VALUE,
      produces = APPLICATION_FHIR_JSON_VALUE)
  @Operation(
      summary = "Start a transfer process",
      description = "Start the transfer of a patient's bundles",
      parameters = {
        @Parameter(
            name = "project",
            schema = @Schema(implementation = String.class),
            description = "Project name")
      },
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Bundle with patient data",
              content =
                  @Content(
                      mediaType = "application/fhir",
                      schema = @Schema(implementation = Bundle.class))),
      responses = {
        @ApiResponse(
            responseCode = "202",
            headers =
                @Header(
                    name = "Content-Location",
                    description = "Link to process status",
                    schema = @Schema(implementation = URI.class)),
            description = "The transfer has started successfully"),
        @ApiResponse(responseCode = "404", description = "The project could not be found")
      })
  Mono<ResponseEntity<Object>> start(
      @PathVariable("project") String project,
      @Valid @NotNull @RequestBody Mono<Bundle> data,
      UriComponentsBuilder uriBuilder) {

    var process = findProcess(project);
    return process
        .map(transferProcessDefinition -> startProcess(data, uriBuilder, transferProcessDefinition))
        .orElseGet(
            () ->
                notFound(
                    new IllegalArgumentException(
                        "Project '%s' could not be found".formatted(project))));
  }

  private Mono<ResponseEntity<Object>> startProcess(
      Mono<Bundle> data,
      UriComponentsBuilder uriBuilder,
      TransferProcessDefinition transferProcessDefinition) {
    return data.map(TransferProcessController::fromPlainBundle)
        .doOnNext(b -> log.debug("Running process: {}", transferProcessDefinition))
        .map(tb -> processRunner.start(transferProcessDefinition, Mono.just(tb)))
        .doOnNext(id -> log.trace("projectId {}", id))
        .map(id -> generateJobUri(uriBuilder, id))
        .doOnNext(jobUri -> log.trace("jobUri {}", jobUri))
        .map(
            jobUri ->
                ResponseEntity.accepted()
                    .headers(h -> h.add(CONTENT_LOCATION, jobUri.toString()))
                    .build());
  }

  static TransportBundle fromPlainBundle(Bundle bundle) {
    log.trace("Converting from PlainBundle to TransportBundle");
    var bundleWithoutParameters =
        resourceStream(bundle)
            .filter(
                not(and(Parameters.class::isInstance, p -> p.getIdPart().equals("transfer-id"))))
            .collect(toBundle());
    var transferId =
        resourceStream(bundle)
            .filter(Parameters.class::isInstance)
            .filter(p -> p.getIdPart().equals("transfer-id"))
            .map(Parameters.class::cast)
            .findFirst()
            .map(resource -> resource.getParameter("id"))
            .map(ParametersParameterComponent::getValue)
            .map(StringType.class::cast)
            .map(PrimitiveType::getValue)
            .orElseThrow(
                () -> new IllegalArgumentException("Parameters 'transfer-id/id' not found"));
    return new TransportBundle(bundleWithoutParameters, transferId);
  }

  private Optional<TransferProcessDefinition> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }

  private URI generateJobUri(UriComponentsBuilder uriBuilder, String id) {
    return uriBuilder.replacePath("api/v2/process/status/{id}").build(id);
  }

  @GetMapping("/process/status/{processId:[\\w-]+}")
  @Operation(
      summary = "Transfer process status",
      parameters = {
        @Parameter(
            name = "processId",
            schema = @Schema(implementation = String.class),
            description = "Transfer process ID")
      },
      responses = {
        @ApiResponse(
            responseCode = "200",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Status.class))),
        @ApiResponse(responseCode = "404", description = "The project could not be found")
      })
  Mono<ResponseEntity<Status>> status(@PathVariable("processId") String processId) {
    log.trace("Process ID: {}", processId);
    return processRunner
        .status(processId)
        .map(s -> responseForStatus(s).body(s))
        .onErrorResume(ErrorResponseUtil::notFound);
  }

  private BodyBuilder responseForStatus(Status s) {
    return switch (s.phase()) {
      case RUNNING ->
          ResponseEntity.accepted()
              .headers(
                  h -> {
                    h.add(X_PROGRESS, "Running");
                    h.add(RETRY_AFTER, "3");
                  });
      case COMPLETED -> ResponseEntity.ok();
      case ERROR -> ResponseEntity.internalServerError();
    };
  }

  @GetMapping("/projects")
  @Operation(
      summary = "List available projects",
      responses = {
        @ApiResponse(
            responseCode = "200",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = String.class),
                    examples = {
                      @ExampleObject(
                          name = "Example list of projects",
                          value = "[\"project1\", \"project2\"]")
                    }))
      })
  ResponseEntity<List<String>> projects() {
    return ResponseEntity.ok()
        .body(processes.stream().map(TransferProcessDefinition::project).toList());
  }

  @GetMapping(value = "projects/{project:[\\w-]+}")
  @Operation(
      summary = "Project configuration",
      parameters = {@Parameter(name = "project", description = "Project name")},
      responses = {
        @ApiResponse(
            responseCode = "200",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransferProcessConfig.class),
                    examples = {
                      @ExampleObject(
                          name = "Example configuration",
                          value =
                              """
                    {"cohortSelector":{"trustCenterAgent":{"server":{"baseUrl":"http://tc-agent:8080"},"domain":"MII","patientIdentifierSystem":"https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym","policySystem":"https://ths-greifswald.de/fhir/CodeSystem/gics/Policy","policies":["IDAT_erheben","IDAT_speichern_verarbeiten","MDAT_erheben","MDAT_speichern_verarbeiten"]}},
                     "dataSelector":{"everything":{"fhirServer":{"baseUrl":"http://cd-hds:8080/fhir"},"resolve":{"patientIdentifierSystem":"http://fts.smith.care"}}},
                     "deidentificator":{"deidentifhir":{"trustCenterAgent":{"server":{"baseUrl":"http://tc-agent:8080"},"domains":{"pseudonym":"MII","salt":"MII","dateShift":"MII"}},"maxDateShift":"P14D","deidentifhirConfig":"/app/projects/example/deidentifhir/CDtoTransport.profile","scraperConfig":"/app/projects/example/deidentifhir/IDScraper.profile"}},
                     "bundleSender":{"researchDomainAgent":{"server":{"baseUrl":"http://rd-agent:8080"},"project":"example"}}}
                    """)
                    })),
        @ApiResponse(responseCode = "404", description = "The project does not exist")
      })
  ResponseEntity<TransferProcessConfig> project(@PathVariable("project") String project) {
    var process = findProcess(project);
    if (process.isPresent()) {
      return ResponseEntity.ok().body(process.get().rawConfig());
    } else {
      log.warn("Project '{}' does not exist", project);
      return ResponseEntity.of(
              ProblemDetail.forStatusAndDetail(
                  NOT_FOUND, "Project '%s' does not exist".formatted(project)))
          .build();
    }
  }
}
