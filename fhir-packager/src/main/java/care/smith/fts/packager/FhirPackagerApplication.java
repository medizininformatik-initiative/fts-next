package care.smith.fts.packager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

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
 *   <li>Implements CommandLineRunner for CLI execution</li>
 *   <li>Provides proper exit codes for different scenarios</li>
 * </ul>
 */
@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
public class FhirPackagerApplication implements CommandLineRunner {

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
   * In Phase 1, it simply logs startup and shutdown to verify the application
   * initializes correctly. Future phases will implement the actual CLI logic.
   * 
   * @param args command-line arguments passed to the application
   * @throws Exception if processing fails
   */
  @Override
  public void run(String... args) throws Exception {
    log.info("FHIR Packager starting...");
    
    // Phase 1: Basic startup verification
    // Future phases will implement:
    // - CLI argument parsing with Picocli
    // - FHIR Bundle processing
    // - Pseudonymizer service integration
    
    log.info("FHIR Packager completed successfully");
  }
}