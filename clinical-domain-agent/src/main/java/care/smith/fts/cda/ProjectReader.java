package care.smith.fts.cda;

import static java.nio.file.Files.*;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProjectReader {

  private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(?<name>.*)[.](?:ya?ml|json)$");

  private final TransferProcessFactory processFactory;
  private final ObjectMapper objectMapper;
  private final Path projectsDir;

  public ProjectReader(
      TransferProcessFactory processFactory,
      @Qualifier("transferProcessObjectMapper") ObjectMapper objectMapper,
      Path projectsDir) {
    this.processFactory = processFactory;
    this.objectMapper = objectMapper;
    this.projectsDir = projectsDir;
  }

  @Bean
  public List<TransferProcessDefinition> createTransferProcesses() throws IOException {
    log.trace("Reading project files from {}", projectsDir);
    try (var files = Files.list(projectsDir)) {
      return files
          .filter(this::matchesFilePattern)
          .filter(withWarning(Files::isRegularFile, "File %s is not a regular file"))
          .filter(withWarning(Files::isReadable, "File %s is not readable"))
          .map(this::createConfigAndProcess)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .toList();
    }
  }

  private static <T> Predicate<T> withWarning(Predicate<T> predicate, String warnFormat) {
    return p -> {
      boolean test = predicate.test(p);
      if (!test) log.warn(warnFormat, p);
      return test;
    };
  }

  private Optional<TransferProcessDefinition> createConfigAndProcess(Path projectFile) {
    var matcher = FILE_NAME_PATTERN.matcher(projectFile.getFileName().toString());
    if (matcher.find()) {
      return openConfigAndParse(projectFile, matcher.group("name"));
    } else {
      log.error("Could not determine project name for file {}", projectFile);
      return empty();
    }
  }

  private Optional<TransferProcessDefinition> openConfigAndParse(Path projectFile, String name) {
    try (var inStream = newInputStream(projectFile)) {
      return parseConfig(inStream, name).flatMap(config -> createProcess(config, name));
    } catch (IOException e) {
      log.error("Unable to read '{}' project's configuration", name, e);
      return empty();
    }
  }

  private Optional<TransferProcessDefinition> createProcess(
      TransferProcessConfig config, String name) {
    try {
      log.info("Project '{}' created: {}", name, config);
      return ofNullable(processFactory.create(config, name));
    } catch (Exception e) {
      log.error("Could not create project '{}'", name, e);
      return empty();
    }
  }

  private Optional<TransferProcessConfig> parseConfig(InputStream inStream, String name) {
    try {
      return Optional.of(objectMapper.readValue(inStream, TransferProcessConfig.class));
    } catch (IOException e) {
      log.error("Unable to parse '{}' project's configuration", name, e);
      return empty();
    }
  }

  private boolean matchesFilePattern(Path p) {
    return FILE_NAME_PATTERN.matcher(p.toString()).find();
  }
}
