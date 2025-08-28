package care.smith.fts.packager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Service for reading FHIR Bundle data from standard input.
 * 
 * <p>This service handles reading the complete input stream from stdin
 * using proper UTF-8 encoding and buffering for efficient processing.
 * The entire input is read into memory as a single string.
 */
@Slf4j
@Service
public class StdinReader {

  /**
   * Reads the complete input from standard input.
   * 
   * <p>This method reads all available data from stdin using a BufferedReader
   * with UTF-8 encoding. The entire input is accumulated into a StringBuilder
   * and returned as a single string.
   *
   * @return the complete input as a string, or empty string if no input available
   * @throws IOException if an error occurs while reading from stdin
   */
  public String readFromStdin() throws IOException {
    log.debug("Starting to read from stdin");
    
    StringBuilder inputBuilder = new StringBuilder();
    
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      
      String line;
      boolean firstLine = true;
      
      while ((line = reader.readLine()) != null) {
        if (!firstLine) {
          inputBuilder.append(System.lineSeparator());
        }
        inputBuilder.append(line);
        firstLine = false;
      }
    }
    
    String input = inputBuilder.toString();
    log.debug("Successfully read {} characters from stdin", input.length());
    
    return input;
  }
}