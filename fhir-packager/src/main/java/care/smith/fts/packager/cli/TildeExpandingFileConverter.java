package care.smith.fts.packager.cli;

import picocli.CommandLine.ITypeConverter;

import java.io.File;

/**
 * Picocli type converter that expands tilde (~) to the user's home directory.
 * 
 * <p>This converter handles the common Unix/Linux convention of using ~ to represent
 * the user's home directory in file paths. It supports:
 * <ul>
 *   <li>{@code ~/path/to/file} - expands to {@code /home/user/path/to/file}</li>
 *   <li>{@code ~} alone - expands to {@code /home/user}</li>
 *   <li>Regular paths without ~ - passed through unchanged</li>
 * </ul>
 * 
 * <p>This converter makes the CLI more user-friendly by allowing users to use
 * the familiar tilde shorthand when specifying file paths.
 * 
 * @see picocli.CommandLine.ITypeConverter
 */
public class TildeExpandingFileConverter implements ITypeConverter<File> {

  /**
   * Converts a string path to a File, expanding tilde notation if present.
   * 
   * @param value the string path that may contain tilde notation
   * @return a File object with tilde expanded to the user's home directory
   * @throws IllegalArgumentException if the value cannot be processed
   */
  @Override
  public File convert(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    
    String expanded = expandTilde(value);
    return new File(expanded);
  }
  
  /**
   * Expands tilde notation in a file path string.
   * 
   * <p>This method handles the following cases:
   * <ul>
   *   <li>{@code ~/...} - replaces ~ with user's home directory</li>
   *   <li>{@code ~} alone - returns user's home directory</li>
   *   <li>Other paths - returned unchanged</li>
   * </ul>
   * 
   * @param path the original path that may contain tilde
   * @return the path with tilde expanded to the actual home directory
   * @throws IllegalArgumentException if home directory cannot be determined
   */
  private String expandTilde(String path) {
    if (!path.startsWith("~")) {
      return path; // No tilde, return as-is
    }
    
    String userHome = System.getProperty("user.home");
    if (userHome == null || userHome.trim().isEmpty()) {
      throw new IllegalArgumentException("Cannot expand tilde: user.home system property is not set");
    }
    
    if (path.equals("~")) {
      // Just ~ alone
      return userHome;
    } else if (path.startsWith("~/")) {
      // ~/something - replace ~ with home directory
      return userHome + path.substring(1);
    } else {
      // ~something else (like ~username) - not supported, return as-is
      // This could be enhanced to support ~username expansion if needed
      return path;
    }
  }
}