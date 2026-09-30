package care.smith.fts.util.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.web.reactive.function.client.WebClient.builder;
import static reactor.test.StepVerifier.create;

import care.smith.fts.util.auth.HttpClientAuth.Config;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

@WireMockTest
public class HttpClientBasicAuthTest {

  @Test
  public void deserialization() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

    var config =
        """
        basic:
          user: user-090058
          password: pass-090130
        """;

    assertThat(om.readValue(config, Config.class)).isNotNull();
  }

  @Test
  public void clientCreated() {
    var config = new HttpClientBasicAuth.Config("user-090058", "pass-090130");
    var auth = new HttpClientBasicAuth();

    var client = builder();

    assertThatNoException().isThrownBy(() -> auth.configure(config, client));
  }

  @Test
  void sendsBasicAuthHeader(WireMockRuntimeInfo wireMockRuntime) {
    wireMockRuntime
        .getWireMock()
        .register(get("/").withBasicAuth("user-090058", "pass-090130").willReturn(ok()));
    var builder = builder().baseUrl(wireMockRuntime.getHttpBaseUrl());

    new HttpClientBasicAuth()
        .configure(new HttpClientBasicAuth.Config("user-090058", "pass-090130"), builder);

    create(builder.build().get().retrieve().toBodilessEntity())
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(OK))
        .verifyComplete();
  }
}
