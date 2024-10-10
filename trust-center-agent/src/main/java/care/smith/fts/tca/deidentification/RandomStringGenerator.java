package care.smith.fts.tca.deidentification;

import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class RandomStringGenerator {
  private static final String ALLOWED_PSEUDONYM_CHARS =
      "0123456789abcdefghijklmnopqrstuvwxyz-_ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private final RandomGenerator randomGenerator;

  public RandomStringGenerator(RandomGenerator randomGenerator) {
    this.randomGenerator = randomGenerator;
  }

  public String generate() {
    return randomGenerator
        .ints(9, 0, ALLOWED_PSEUDONYM_CHARS.length())
        .mapToObj(ALLOWED_PSEUDONYM_CHARS::charAt)
        .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
        .toString();
  }
}
