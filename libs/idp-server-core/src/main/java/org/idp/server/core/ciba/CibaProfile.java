package org.idp.server.core.ciba;

public enum CibaProfile {
  CIBA,
  FAPI_CIBA;

  public boolean isCiba() {
    return this == CIBA;
  }

  public boolean isFapiCiba() {
    return this == FAPI_CIBA;
  }
}
