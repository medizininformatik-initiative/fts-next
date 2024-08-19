package care.smith.fts.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TidPidMap {
  public static String getTidPidMapAsJson() throws IOException {
    try (InputStream inputStream = TidPidMap.class.getResourceAsStream("tid-pid-map.json")) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
