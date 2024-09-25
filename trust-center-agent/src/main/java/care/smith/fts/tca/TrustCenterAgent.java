package care.smith.fts.tca;

import care.smith.fts.util.AgentConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(AgentConfiguration.class)
public class TrustCenterAgent {

  public static void main(String... args) {
    SpringApplication.run(TrustCenterAgent.class, args);
  }
}
