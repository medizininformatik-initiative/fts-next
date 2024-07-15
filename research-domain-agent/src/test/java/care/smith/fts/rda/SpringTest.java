package care.smith.fts.rda;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SpringTest {
  @Autowired FhirContext fhirContext;

  @Test
  public void startSpringApplication() {
    assertThat(fhirContext).isNotNull();
  }
}
