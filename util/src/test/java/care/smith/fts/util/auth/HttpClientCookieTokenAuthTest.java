package care.smith.fts.util.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.http.HttpHeaders.COOKIE;
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
public class HttpClientCookieTokenAuthTest {

  @Test
  public void deserialization() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

    var config =
        """
        cookieToken:
          token: token-090112
        """;

    assertThat(om.readValue(config, Config.class)).isNotNull();
  }

  @Test
  public void clientCreated() {
    var impl = new HttpClientCookieTokenAuth();

    assertThatNoException()
        .isThrownBy(
            () -> impl.configure(new HttpClientCookieTokenAuth.Config("token-090112"), builder()));
  }

  @Test
  void sendsCookieHeader(WireMockRuntimeInfo wireMockRuntime) {
    wireMockRuntime
        .getWireMock()
        .register(get("/").withHeader(COOKIE, equalTo("token-090112")).willReturn(ok()));
    var builder = builder().baseUrl(wireMockRuntime.getHttpBaseUrl());

    new HttpClientCookieTokenAuth()
        .configure(new HttpClientCookieTokenAuth.Config("token-090112"), builder);

    create(builder.build().get().retrieve().toBodilessEntity())
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(OK))
        .verifyComplete();
  }
}
