package org.idp.server.core.oidc.factory;

public enum RequestObjectFactoryType {
  DEFAULT,
  FAPI;

  public boolean isFapi() {
    return this == FAPI;
  }

  public boolean isDefault() {
    return this == DEFAULT;
  }
}
