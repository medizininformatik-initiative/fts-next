package care.smith.fts.util.fhir;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Slf4j
@Configuration
@Import({FhirDecoder.class, FhirEncoder.class})
public class FhirCodecConfiguration {

  @Bean
  public WebFluxConfigurer fhirServerCodecConfigurer(FhirDecoder decoder, FhirEncoder encoder) {
    return new WebFluxConfigurer() {
      @Override
      public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.customCodecs().register(decoder);
        configurer.customCodecs().register(encoder);
      }
    };
  }

  @Bean
  public WebClientCustomizer fhirWebClientCodecCustomizer(
      FhirDecoder decoder, FhirEncoder encoder) {
    return builder -> {
      builder.codecs(conf -> conf.customCodecs().register(decoder));
      builder.codecs(conf -> conf.customCodecs().register(encoder));
      log.debug("WebClientFhirCodec registered");
    };
  }
}
