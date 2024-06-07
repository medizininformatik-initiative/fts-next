package care.smith.fts.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

public class TemplateLoader {
  static InputStream readFile(String filename, ClassLoader classLoader) {
    return classLoader.getResourceAsStream(filename);
  }

  static CharBuffer getCharBuffer(String fileName, ClassLoader classLoader) throws IOException {
    try (InputStream g = readFile(fileName, classLoader)) {
      return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(g.readAllBytes()));
    }
  }
}
