package org.idp.server.core.federation;

public enum SupportedFederationType {
  OIDC("oidc"),
  SAML("saml");

  String type;

  SupportedFederationType(String type) {
    this.type = type;
  }

  public FederationType toFederationType() {
    return new FederationType(name());
  }
}
