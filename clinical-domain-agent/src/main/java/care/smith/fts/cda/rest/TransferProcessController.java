package care.smith.fts.cda.rest;

import static care.smith.fts.util.HeaderTypes.X_PROGRESS;
import static care.smith.fts.util.error.ErrorResponseUtil.notFound;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import care.smith.fts.cda.TransferProcessConfig;
import care.smith.fts.cda.TransferProcessDefinition;
import care.smith.fts.cda.TransferProcessRunner;
import care.smith.fts.cda.TransferProcessStatus;
import care.smith.fts.util.error.ErrorResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
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
@Tag(name = "Transfer Process API", description = "API for managing transfer processes")
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
      value = "/process/{project:[\\w-]+}/start",
      consumes = APPLICATION_JSON_VALUE,
      produces = APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Start a transfer process",
      description =
          """
          **Since 5.0**

          Start a transfer of patients with IDs given in the request body or if empty start
           a transfer of all consented patients.
          """,
      parameters = {
        @Parameter(
            name = "project",
            required = true,
            schema = @Schema(implementation = String.class),
            description = "Project name")
      },
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "IDs of patients to transfer",
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = String.class),
                      examples =
                          @ExampleObject(
                              name = "List of three patient IDs",
                              value = "[\"id1\", \"id2\", \"id3\"]"))),
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
      @Valid @RequestBody(required = false) List<String> pids,
      UriComponentsBuilder uriBuilder) {
    var process = findProcess(project);
    if (process.isPresent()) {
      log.debug("Running process: {}", process.get());

      var id = processRunner.start(process.get(), Optional.ofNullable(pids).orElse(List.of()));
      var jobUri = generateJobUri(uriBuilder, id);
      return processRunner
          .status(id)
          .map(
              s ->
                  ResponseEntity.accepted()
                      .headers(h -> h.add("Content-Location", jobUri.toString()))
                      .build());
    } else {
      log.warn("Project '{}' not found", project);
      return notFound(
          new IllegalArgumentException("Project '%s' could not be found".formatted(project)));
    }
  }

  private URI generateJobUri(UriComponentsBuilder uriBuilder, String id) {
    return uriBuilder.replacePath("api/v2/process/status/{id}").build(id);
  }

  @GetMapping("/process/status/{processId:[\\w-]+}")
  @Operation(
      summary = "Transfer process's status",
      description = "**Since 5.0**\n\n",
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
                    schema = @Schema(implementation = TransferProcessStatus.class),
                    examples = {
                      @ExampleObject(
                          name = "Transfer process status",
                          value =
"""
{"processId":"ChlblQ","phase":"RUNNING","createdAt":[2024,12,16,9,29,37,186662587],"finishedAt":null,"totalPatients":100,"totalBundles":53,"deidentifiedBundles":32,"sentBundles":0,"skippedBundles":0}
""")
                    })),
        @ApiResponse(responseCode = "404", description = "The project could not be found")
      })
  Mono<ResponseEntity<TransferProcessStatus>> status(
      @PathVariable(value = "processId") String processId) {
    return processRunner
        .status(processId)
        .map(s -> responseForStatus(s).body(s))
        .onErrorResume(ErrorResponseUtil::notFound);
  }

  @GetMapping("/process/statuses")
  @Operation(
      summary = "List of all transfer process statuses",
      description = "**Since 5.0**\n\n",
      responses = {
        @ApiResponse(
            responseCode = "200",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransferProcessStatus.class),
                    examples = {
                      @ExampleObject(
                          name = "List of transfer process statuses",
                          value =
"""
[
  {"processId":"8R9JGu","phase":"COMPLETED","createdAt":[2024,12,16,9,28,50,772443200],"finishedAt":[2024,12,16,9,29,31,776068091],"totalPatients":100,"totalBundles":119,"deidentifiedBundles":118,"sentBundles":118,"skippedBundles":0},
  {"processId":"ChlblQ","phase":"RUNNING","createdAt":[2024,12,16,9,29,37,186662587],"finishedAt":null,"totalPatients":100,"totalBundles":53,"deidentifiedBundles":32,"sentBundles":0,"skippedBundles":0}
]
""")
                    })),
      })
  Mono<ResponseEntity<List<TransferProcessStatus>>> statuses() {
    return processRunner.statuses().map(s -> ResponseEntity.ok().body(s));
  }

  private static BodyBuilder responseForStatus(TransferProcessStatus s) {
    return switch (s.phase()) {
      case QUEUED -> ResponseEntity.accepted().headers(h -> h.add(X_PROGRESS, "Queued"));
      case RUNNING -> ResponseEntity.accepted().headers(h -> h.add(X_PROGRESS, "Running"));
      case COMPLETED, COMPLETED_WITH_ERROR -> ResponseEntity.ok();
      case FATAL -> ResponseEntity.internalServerError();
    };
  }

  private Optional<TransferProcessDefinition> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }

  @GetMapping("/projects")
  @Operation(
      summary = "List available projects",
      description = "**Since 5.0**\n\n",
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
      description = "**Since 5.0**\n\n",
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
{
  "cohortSelector":{"trustCenterAgent":{"server":{"baseUrl":"http://tc-agent:8080"},"domain":"MII","patientIdentifierSystem":"https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym","policySystem":"https://ths-greifswald.de/fhir/CodeSystem/gics/Policy","policies":["IDAT_erheben","IDAT_speichern_verarbeiten","MDAT_erheben","MDAT_speichern_verarbeiten"],"signerIdType":"Pseudonym"}},
  "dataSelector":{"everything":{"fhirServer":{"baseUrl":"http://cd-hds:8080/fhir"},"resolve":{"patientIdentifierSystem":"http://fts.smith.care"}}},
  "deidentificator":{"deidentifhir":{"trustCenterAgent":{"server":{"baseUrl":"http://tc-agent:8080"},"domains":{"pseudonym":"MII","salt":"MII","dateShift":"MII"}},"maxDateShift":"P14D","deidentifhirConfig":"/app/projects/example/deidentifhir/CDtoTransport.profile","scraperConfig":"/app/projects/example/deidentifhir/IDScraper.profile"}},
  "bundleSender":{"researchDomainAgent":{"server":{"baseUrl":"http://rd-agent:8080"},"project":"example"}}
}
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
