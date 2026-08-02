package care.smith.fts.tca.services;

import java.util.Objects;

/**
 * Mapping between a transport ID and its corresponding secure pseudonym.
 *
 * <p>This record represents a single tID→sID mapping stored in Redis for later resolution by the
 * Research Domain Agent.
 *
 * @param transportId The transport ID (tID) - temporary identifier returned to CDA
 * @param securePseudonym The real pseudonym (sID) from the backend (gPAS/Vfps/entici)
 * @param domain The pseudonymization domain/namespace
 * @param transferId The session grouping identifier
 */
public record TransportIdMapping(
    String transportId, String securePseudonym, String domain, String transferId) {

  public TransportIdMapping {
    Objects.requireNonNull(transportId, "transportId is required");
    Objects.requireNonNull(securePseudonym, "securePseudonym is required");
    Objects.requireNonNull(domain, "domain is required");
    Objects.requireNonNull(transferId, "transferId is required");
  }
}
