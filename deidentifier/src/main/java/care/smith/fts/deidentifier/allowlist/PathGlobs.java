package care.smith.fts.deidentifier.allowlist;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The keys of a {@code paths} section: each key is a glob over the base paths of its module, and a
 * key without {@code *} stands for itself.
 */
interface PathGlobs {

  /**
   * The key that reaches each base path that some key reaches.
   *
   * @throws IllegalStateException when a key matches no base path, or two keys reach the same base
   *     path: a path has one handler per module
   */
  static Map<String, String> keyPerPath(Set<String> keys, Set<String> base) {
    Map<String, List<String>> keysByPath =
        keys.stream()
            .flatMap(key -> expand(key, base).stream().map(path -> Map.entry(path, key)))
            .collect(groupingBy(Entry::getKey, mapping(Entry::getValue, toList())));
    keysByPath.entrySet().stream()
        .filter(e -> e.getValue().size() > 1)
        .findFirst()
        .ifPresent(
            e -> {
              throw new IllegalStateException(
                  "The paths entries %s both reach the path %s, but a path has one handler!"
                      .formatted(String.join(" and ", e.getValue()), e.getKey()));
            });
    return keysByPath.entrySet().stream()
        .collect(toUnmodifiableMap(Entry::getKey, e -> e.getValue().getFirst()));
  }

  private static List<String> expand(String key, Set<String> base) {
    Pattern pattern = Pattern.compile(globToRegex(key));
    List<String> matches = base.stream().filter(path -> pattern.matcher(path).matches()).toList();
    if (matches.isEmpty()) {
      throw new IllegalStateException(
          "The paths entry %s matches no path of the base list!".formatted(key));
    }
    return matches;
  }

  /**
   * A {@code *} inside a path element matches within that element. A leading {@code *.} also
   * matches any number of whole leading elements, a trailing {@code .*} any number of whole
   * trailing elements, and a lone {@code *} every path. Everything else is literal: path keys carry
   * regex metacharacters, e.g. the brackets of {@code deceased[dateTime]}.
   */
  private static String globToRegex(String glob) {
    if (glob.equals("*")) {
      return ".*";
    }
    String[] elements = glob.split("\\.", -1);
    boolean leading = elements[0].equals("*");
    boolean trailing = elements[elements.length - 1].equals("*");
    String inner =
        Arrays.stream(elements, leading ? 1 : 0, trailing ? elements.length - 1 : elements.length)
            .map(PathGlobs::elementToRegex)
            .collect(joining("\\."));
    return (leading ? "(|.*\\.)" : "") + inner + (trailing ? "(|\\..*)" : "");
  }

  private static String elementToRegex(String element) {
    return Arrays.stream(element.split("\\*", -1)).map(Pattern::quote).collect(joining("[^.]*"));
  }
}
