package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.Set;

@JsonClassDescription("")
public record TcaCohortSelectorConfig(
    /* */
    @NotNull HttpClientConfig server,
    /* */
    @NotNull String patientIdentifierSystem,
    /* */
    @NotNull String policySystem,

    /* */
    Set<String> policies,

    /* */
    String domain,

    /* */
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
