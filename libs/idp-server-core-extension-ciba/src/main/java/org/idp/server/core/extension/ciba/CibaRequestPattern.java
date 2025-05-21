package org.idp.server.core.extension.ciba;

public enum CibaRequestPattern {
  NORMAL,
  REQUEST_OBJECT;

  public boolean isRequestParameter() {
    return this == REQUEST_OBJECT;
  }
}
