package org.idp.server.core.security.hook.ssf;

public class SharedSignalDecider {

  SharedSignalFrameworkConfiguration configuration;
  SecurityEventTokenEntity securityEventTokenEntity;

  public SharedSignalDecider(
      SharedSignalFrameworkConfiguration configuration,
      SecurityEventTokenEntity securityEventTokenEntity) {
    this.configuration = configuration;
    this.securityEventTokenEntity = securityEventTokenEntity;
  }

  public boolean decide() {
    if (!configuration.exists()) {
      return false;
    }
    if (!securityEventTokenEntity.isDefinedEvent()) {
      return false;
    }

    return configuration.containsEvent(securityEventTokenEntity.securityEventAsString());
  }
}
