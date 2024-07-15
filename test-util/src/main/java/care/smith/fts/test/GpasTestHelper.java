package care.smith.fts.test;

import static java.util.List.of;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Map.Entry;

public class GpasTestHelper {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static String pseudonymizeAllowCreate(String domain, Map<String, String> pseudonyms)
      throws JsonProcessingException {
    var parameters = pseudonyms.entrySet().stream().map(e -> forPseudonym(domain, e)).toList();
    var response = ofEntries(entry("resourceType", "Parameters"), entry("parameter", parameters));

    return objectMapper.writeValueAsString(response);
  }

  private static Map<String, Object> forPseudonym(String domain, Entry<String, String> e) {
    return ofEntries(
        entry("name", "pseudonym"),
        entry(
            "part",
            of(
                part("original", e.getKey()),
                part("domain", domain),
                part("pseudonym", e.getValue()))));
  }

  private static Map<String, Object> part(String name, String value) {
    return ofEntries(
        entry("name", name),
        entry(
            "valueIdentifier",
            ofEntries(entry("system", "https://ths-greifswald.de/gpas"), entry("value", value))));
  }
}
