package org.idp.server.core.oidc.federation;

public enum StandardSupportedFederationType {
  OIDC("oidc"),
  SAML("saml");

  String type;

  StandardSupportedFederationType(String type) {
    this.type = type;
  }

  public FederationType toFederationType() {
    return new FederationType(type);
  }
}
