package care.smith.fts.util.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static java.time.Duration.ofHours;
import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.security.oauth2.client.registration.ClientRegistration.withRegistrationId;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS;
import static org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER;
import static org.springframework.web.reactive.function.client.WebClient.builder;
import static reactor.test.StepVerifier.create;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import reactor.core.publisher.Mono;

@WireMockTest
class HttpClientOAuth2AuthTest {

  @Test
  void sendsBearerTokenOfRegistration(WireMockRuntimeInfo wireMockRuntime) {
    wireMockRuntime
        .getWireMock()
        .register(
            get("/")
                .withHeader(AUTHORIZATION, equalTo("Bearer token-of-registration-101512"))
                .willReturn(ok()));
    var builder = builder().baseUrl(wireMockRuntime.getHttpBaseUrl());

    new HttpClientOAuth2Auth(clientManager())
        .configure(new HttpClientOAuth2Auth.Config("registration-101512"), builder);

    create(builder.build().get().retrieve().toBodilessEntity())
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(OK))
        .verifyComplete();
  }

  /** Issues a token that names the requested registration, so the test sees which one was used. */
  private static ReactiveOAuth2AuthorizedClientManager clientManager() {
    return request -> {
      var registrationId = request.getClientRegistrationId();
      var registration =
          withRegistrationId(registrationId)
              .clientId("client-101512")
              .authorizationGrantType(CLIENT_CREDENTIALS)
              .tokenUri("http://localhost/token")
              .build();
      var token =
          new OAuth2AccessToken(
              BEARER, "token-of-" + registrationId, now(), now().plus(ofHours(1)));
      var principal = request.getPrincipal().getName();
      return Mono.just(new OAuth2AuthorizedClient(registration, principal, token));
    };
  }
}
