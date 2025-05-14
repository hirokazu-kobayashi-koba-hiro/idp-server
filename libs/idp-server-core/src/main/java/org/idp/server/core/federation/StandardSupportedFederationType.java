package org.idp.server.core.federation;

public enum StandardSupportedFederationType {
  OIDC("oidc"),
  SAML("saml");

  String type;

  StandardSupportedFederationType(String type) {
    this.type = type;
  }

  public FederationType toFederationType() {
    return new FederationType(name());
  }
}
