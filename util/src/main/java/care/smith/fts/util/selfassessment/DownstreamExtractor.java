package care.smith.fts.util.selfassessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface DownstreamExtractor {

  record Downstream(String label, String baseUrl) {}

  String BASE_URL_KEY = "baseUrl";

  static List<Downstream> extract(Map<String, ?> root) {
    var out = new ArrayList<Downstream>();
    walk("", root, out);
    return List.copyOf(out);
  }

  private static void walk(String path, Object node, List<Downstream> out) {
    if (node instanceof Map<?, ?> m) {
      var url = m.get(BASE_URL_KEY);
      if (url instanceof String s && !s.isBlank()) {
        out.add(new Downstream(path.isEmpty() ? BASE_URL_KEY : path, s));
      }
      for (var e : m.entrySet()) {
        var key = String.valueOf(e.getKey());
        if (BASE_URL_KEY.equals(key)) {
          continue;
        }
        walk(path.isEmpty() ? key : path + "." + key, e.getValue(), out);
      }
    } else if (node instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        walk(path + "[" + i + "]", list.get(i), out);
      }
    }
  }
}
