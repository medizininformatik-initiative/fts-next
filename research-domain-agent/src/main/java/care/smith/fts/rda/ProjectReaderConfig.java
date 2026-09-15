package care.smith.fts.rda;

import care.smith.fts.util.ProjectReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ProjectReaderConfig {

  @Bean
  public List<TransferProcessDefinition> createTransferProcesses(
      TransferProcessFactory processFactory,
      @Qualifier("transferProcessObjectMapper") ObjectMapper objectMapper,
      @Value("${projects.directory:projects}") Path projectsDir,
      @Value("${projects.strict-validation:false}") boolean strictValidation)
      throws IOException {
    return new ProjectReader<TransferProcessConfig, TransferProcessDefinition>(
            processFactory::create,
            objectMapper,
            TransferProcessConfig.class,
            projectsDir,
            strictValidation)
        .createTransferProcesses();
  }
}
