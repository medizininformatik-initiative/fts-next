package care.smith.fts.rda;

import static java.nio.file.Files.*;
import static java.util.Optional.empty;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.WARN;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProjectReader {

  private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(?<name>.+)[.](?:ya?ml|json)$");

  private final TransferProcessFactory processFactory;
  private final ObjectMapper objectMapper;
  private final Path projectsDir;
  private final boolean strictValidation;

  public ProjectReader(
      TransferProcessFactory processFactory,
      @Qualifier("transferProcessObjectMapper") ObjectMapper objectMapper,
      @Value("${projects.directory:projects}") Path projectsDir,
      @Value("${projects.strict-validation:false}") boolean strictValidation) {
    this.processFactory = processFactory;
    this.objectMapper = objectMapper;
    this.projectsDir = projectsDir;
    this.strictValidation = strictValidation;
  }

  @Bean
  public List<TransferProcessDefinition> createTransferProcesses() throws IOException {
    try (var files = Files.list(projectsDir)) {
      return files
          .filter(this::matchesFilePattern)
          .filter(withValidation(Files::isRegularFile, "File %s is not a regular file"))
          .filter(withValidation(Files::isReadable, "File %s is not readable"))
          .map(this::createConfigAndProcess)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .toList();
    }
  }

  private <T> Predicate<T> withValidation(Predicate<T> predicate, String warnFormat) {
    return p -> {
      boolean test = predicate.test(p);
      if (!test) {
        throwOrLog(WARN, warnFormat.formatted(p));
      }
      return test;
    };
  }

  private Optional<TransferProcessDefinition> createConfigAndProcess(Path projectFile) {
    var matcher = FILE_NAME_PATTERN.matcher(projectFile.getFileName().toString());
    if (matcher.find()) {
      return openConfigAndParse(projectFile, matcher.group("name"));
    } else {
      throwOrLog(ERROR, "Could not determine project name for file %s".formatted(projectFile));
      return empty();
    }
  }

  protected Optional<TransferProcessDefinition> openConfigAndParse(Path projectFile, String name) {
    try (var inStream = newInputStream(projectFile)) {
      return parseConfig(inStream, name).flatMap(config -> createProcess(config, name));
    } catch (IOException e) {
      throwOrLog(ERROR, "Unable to read '%s' project's configuration".formatted(name), e);
      return empty();
    }
  }

  private Optional<TransferProcessDefinition> createProcess(
      TransferProcessConfig config, String name) {
    try {
      log.info("Project '{}' created: {}", name, config);
      return Optional.of(processFactory.create(config, name));
    } catch (Exception e) {
      throwOrLog(ERROR, "Could not create project '%s'".formatted(name), e);
      return empty();
    }
  }

  private Optional<TransferProcessConfig> parseConfig(InputStream inStream, String name) {
    try {
      return Optional.of(objectMapper.readValue(inStream, TransferProcessConfig.class));
    } catch (IOException e) {
      throwOrLog(ERROR, "Unable to parse '%s' project's configuration".formatted(name), e);
      return empty();
    }
  }

  private void throwOrLog(Level level, String msg) {
    throwOrLog(level, msg, null);
  }

  private void throwOrLog(Level level, String msg, Throwable cause) {
    if (strictValidation) {
      throw new ProjectConfigurationException(msg, cause);
    } else {
      if (cause == null) {
        log.atLevel(level).log(msg);
      } else {
        log.atLevel(level).setCause(cause).log(msg);
      }
    }
  }

  private boolean matchesFilePattern(Path p) {
    return FILE_NAME_PATTERN.matcher(p.toString()).find();
  }
}
