package org.idp.server.type.oauth;

public enum TokenType {
  Bearer,
  DPoP,
  undefined;

  public boolean isDefined() {
    return this != undefined;
  }
}
