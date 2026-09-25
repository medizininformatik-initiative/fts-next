package care.smith.fts.cda;

import static com.google.common.base.Throwables.getCausalChain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import care.smith.fts.util.ProjectConfigurationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = NONE)
class ProjectReaderConfigTest {

  @Test
  void projectsDirectoryYieldsTransferProcessBeans(
      @Autowired List<TransferProcessDefinition> transferProcesses) {
    assertThat(transferProcesses)
        .extracting(TransferProcessDefinition::project)
        .contains("example");
  }

  @Test
  void strictValidationRejectsBrokenProject() {
    var thrown =
        catchThrowable(
            () ->
                new SpringApplicationBuilder(ClinicalDomainAgent.class)
                    .web(WebApplicationType.NONE)
                    .run(
                        "--projects.directory=src/test/resources/strict-projects",
                        "--projects.strict-validation=true"));

    assertThat(getCausalChain(thrown))
        .hasAtLeastOneElementOfType(ProjectConfigurationException.class);
  }
}
