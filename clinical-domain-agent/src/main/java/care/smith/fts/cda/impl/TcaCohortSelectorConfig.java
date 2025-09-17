package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.Set;

@JsonClassDescription("Configuration for TCA cohort selector")
public record TcaCohortSelectorConfig(
    /* Trust Center Agent server configuration */
    @NotNull HttpClientConfig server,
    /* Patient identifier system used for patient identification */
    @NotNull String patientIdentifierSystem,
    /* Policy system URL for consent policies */
    @NotNull String policySystem,

    /* Set of consent policies to check */
    Set<String> policies,

    /* Domain identifier for consent queries */
    String domain,

    /* Type of identifier used for consent signing (defaults to "Pseudonym" if not specified) */
    String signerIdType) {

  public TcaCohortSelectorConfig(
      HttpClientConfig server,
      String patientIdentifierSystem,
      String policySystem,
      Set<String> policies,
      String domain,
      String signerIdType) {
    this.server = server;
    this.patientIdentifierSystem = patientIdentifierSystem;
    this.policySystem = policySystem;
    this.policies = policies;
    this.domain = domain;
    this.signerIdType = Optional.ofNullable(signerIdType).orElse("Pseudonym");
  }
}
