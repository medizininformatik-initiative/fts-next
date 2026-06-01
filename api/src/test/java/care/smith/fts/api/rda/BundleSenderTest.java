package care.smith.fts.api.rda;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class BundleSenderTest {

  /**
   * A sender that overrides nothing, exercising the interface's default methods. Uses an anonymous
   * class (not a lambda) so each call yields a distinct instance — lambdas with no captured state
   * are cached by the JVM and would share one identity.
   */
  private static BundleSender defaultSender() {
    return new BundleSender() {
      @Override
      public Mono<Result> send(Bundle bundles) {
        return Mono.just(new Result());
      }
    };
  }

  @Test
  void defaultDestinationIdIsUngroupedAndPerInstance() {
    var a = defaultSender();
    var b = defaultSender();

    assertThat(a.destinationId()).startsWith("ungrouped-");
    assertThat(a.destinationId()).isEqualTo(a.destinationId());
    assertThat(a.destinationId()).isNotEqualTo(b.destinationId());
  }

  @Test
  void defaultSendConcurrencyMatchesConfigDefault() {
    assertThat(defaultSender().sendConcurrency()).isEqualTo(2);
  }

  @Test
  void defaultSenderSends() {
    assertThat(defaultSender().send(new Bundle()).block()).isNotNull();
  }

  @Test
  void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    om.readValue(
        """
        ---
        """,
        BundleSender.Config.class);
  }

  @Test
  void testInstantiateConfig() {
    assertThat(new BundleSender.Config()).isNotNull();
  }

  @Test
  void testInstantiateResult() {
    assertThat(new BundleSender.Result()).isNotNull();
  }
}
