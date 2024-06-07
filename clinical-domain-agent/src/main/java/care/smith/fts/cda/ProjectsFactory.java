package care.smith.fts.cda;

import static java.nio.file.Files.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProjectsFactory {

  private static final Pattern FILE_NAME_PATTERN = Pattern.compile(".*[.](ya?ml|json)$");

  private final TransferProcessFactory processFactory;
  private final ConfigurableListableBeanFactory beanFactory;
  private final ObjectMapper objectMapper;
  private final Path projectsDir;

  public ProjectsFactory(
      TransferProcessFactory processFactory,
      ConfigurableListableBeanFactory beanFactory,
      @Qualifier("transferProcessObjectMapper") ObjectMapper objectMapper,
      Path projectsDir) {
    this.processFactory = processFactory;
    this.beanFactory = beanFactory;
    this.objectMapper = objectMapper;
    this.projectsDir = projectsDir;
  }

  @PostConstruct
  public void registerProcesses() throws IOException {
    try (var files = newDirectoryStream(projectsDir, this::matchesFilePattern)) {
      for (Path projectFile : files) {
        if (!isRegularFile(projectFile)) {
          log.warn("File %s is not a regular file".formatted(projectFile.toString()));
        } else if (!isReadable(projectFile)) {
          log.warn("File %s is not readable".formatted(projectFile.toString()));
        } else {
          registerProcess(projectFile);
        }
      }
    }
  }

  private boolean matchesFilePattern(Path p) {
    return FILE_NAME_PATTERN.matcher(p.toString()).matches();
  }

  private void registerProcess(Path projectFile) throws IOException {
    var name = projectFile.getFileName().toString().replaceFirst(".ya?ml$", "");
    try (var inStream = newInputStream(projectFile)) {
      var config = objectMapper.readValue(inStream, TransferProcessConfig.class);
      beanFactory.registerSingleton(name, processFactory.create(config));
    }
  }
}
