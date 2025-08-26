package care.smith.fts.packager;

import care.smith.fts.packager.cli.PackagerCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;
import picocli.spring.PicocliSpringFactory;

/**
 * Main application class for the FHIR Packager CLI tool.
 * 
 * <p>This Spring Boot application is designed as a command-line tool that pseudonymizes
 * FHIR Bundles by integrating with an external FHIR Pseudonymizer REST service.
 * The application follows a streaming approach to handle large bundles efficiently.
 * 
 * <p>Key characteristics:
 * <ul>
 *   <li>Runs as a CLI tool (no web server)</li>
 *   <li>Logs to stderr only to keep stdout clean for data output</li>
 *   <li>Uses Picocli for command-line argument parsing and validation</li>
 *   <li>Integrates Picocli with Spring for dependency injection</li>
 *   <li>Provides proper exit codes for different scenarios (0=success, 1=error, 2=invalid args)</li>
 * </ul>
 */
@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
public class FhirPackagerApplication implements CommandLineRunner, ExitCodeGenerator {

  @Autowired
  private PackagerCommand packagerCommand;
  
  @Autowired
  private ApplicationContext applicationContext;
  
  private int exitCode;

  /**
   * Main entry point for the FHIR Packager application.
   * 
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    // Disable web server since this is a CLI application
    System.setProperty("spring.main.web-application-type", "none");
    
    // Configure logging to stderr only to keep stdout clean for data output
    System.setProperty("logging.config", "classpath:logback-spring.xml");
    
    try {
      SpringApplication app = new SpringApplication(FhirPackagerApplication.class);
      
      // Disable banner and web environment for cleaner CLI experience
      app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
      
      int exitCode = SpringApplication.exit(app.run(args));
      System.exit(exitCode);
    } catch (Exception e) {
      log.error("Application failed to start", e);
      System.exit(1);
    }
  }

  /**
   * CommandLineRunner implementation for CLI execution.
   * 
   * <p>This method is called after Spring context initialization.
   * It creates a Picocli CommandLine instance with Spring integration
   * and executes the PackagerCommand with the provided arguments.
   * 
   * @param args command-line arguments passed to the application
   * @throws Exception if processing fails
   */
  @Override
  public void run(String... args) throws Exception {
    log.debug("FHIR Packager starting with args: {}", (Object) args);
    
    try {
      // Create Picocli CommandLine with Spring factory for dependency injection
      CommandLine commandLine = new CommandLine(packagerCommand, new PicocliSpringFactory(applicationContext));
      
      // Execute the command and capture exit code
      exitCode = commandLine.execute(args);
      
      log.debug("FHIR Packager completed with exit code: {}", exitCode);
      
    } catch (Exception e) {
      log.error("Command execution failed", e);
      exitCode = 1;
      throw e;
    }
  }
  
  /**
   * Provides the exit code for the Spring Boot application.
   * 
   * @return the exit code from command execution
   */
  @Override
  public int getExitCode() {
    return exitCode;
  }
}