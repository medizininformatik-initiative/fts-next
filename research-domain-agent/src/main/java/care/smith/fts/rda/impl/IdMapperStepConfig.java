package care.smith.fts.rda.impl;

import care.smith.fts.util.HttpClientConfig;

public record IdMapperStepConfig(TCAConfig trustCenterAgent) {

  record TCAConfig(HttpClientConfig server) {}
}
