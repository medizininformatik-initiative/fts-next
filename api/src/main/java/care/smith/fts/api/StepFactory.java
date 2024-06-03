package care.smith.fts.api;

public interface StepFactory<STEPTYPE, CCONF, ICONF> {
  Class<ICONF> getConfigType();

  STEPTYPE create(CCONF commonConfig, ICONF implConfig);
}
