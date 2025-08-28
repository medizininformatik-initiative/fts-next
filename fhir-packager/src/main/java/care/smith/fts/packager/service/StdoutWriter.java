package care.smith.fts.packager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Service for writing FHIR Bundle data to standard output.
 * 
 * <p>This service handles writing the processed FHIR Bundle data to stdout
 * with proper UTF-8 encoding and ensures the output is flushed immediately.
 * All logging is done to stderr to keep stdout clean for data output.
 */
@Slf4j
@Service
public class StdoutWriter {

  /**
   * Writes the complete output to standard output.
   * 
   * <p>This method writes the provided data to stdout using UTF-8 encoding
   * and immediately flushes the output to ensure it's available to consuming
   * processes. All logging is performed to stderr to avoid contaminating the
   * data output stream.
   *
   * @param output the data to write to stdout
   * @throws IOException if an error occurs while writing to stdout
   */
  public void writeToStdout(String output) throws IOException {
    log.debug("Writing {} characters to stdout", output.length());
    
    try (PrintWriter writer = new PrintWriter(System.out, false, StandardCharsets.UTF_8)) {
      writer.print(output);
      writer.flush();
      
      // Verify flush was successful
      if (writer.checkError()) {
        throw new IOException("Error occurred while writing to stdout");
      }
    }
    
    log.debug("Successfully wrote output to stdout and flushed");
  }
}