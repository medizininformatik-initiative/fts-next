package care.smith.fts.packager.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "pseudonymizer")
@Validated
public record PseudonymizerConfig(
    @NotBlank String url,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @NotNull @Valid RetryConfig retry,
    boolean healthCheckEnabled) {

  @ConstructorBinding
  public PseudonymizerConfig(
      @DefaultValue("http://localhost:8080") String url,
      @DefaultValue("PT10S") Duration connectTimeout,
      @DefaultValue("PT60S") Duration readTimeout,
      @DefaultValue RetryConfig retry,
      @DefaultValue("true") boolean healthCheckEnabled) {
    this.url = url;
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
    this.retry = retry != null ? retry : new RetryConfig();
    this.healthCheckEnabled = healthCheckEnabled;
  }

  public record RetryConfig(
      @Min(1) @Max(10) int maxAttempts,
      @NotNull Duration initialBackoff,
      @NotNull Duration maxBackoff,
      @Positive double backoffMultiplier) {

    public RetryConfig() {
      this(3, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0);
    }

    @ConstructorBinding
    public RetryConfig(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("PT1S") Duration initialBackoff,
        @DefaultValue("PT30S") Duration maxBackoff,
        @DefaultValue("2.0") double backoffMultiplier) {
      this.maxAttempts = maxAttempts;
      this.initialBackoff = initialBackoff;
      this.maxBackoff = maxBackoff;
      this.backoffMultiplier = backoffMultiplier;
    }
  }
}
