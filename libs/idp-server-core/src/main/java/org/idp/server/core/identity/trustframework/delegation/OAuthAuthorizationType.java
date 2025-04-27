package org.idp.server.core.identity.trustframework.delegation;

public enum OAuthAuthorizationType {
  CLIENT_CREDENTIALS("client_credentials"),
  RESOURCE_OWNER_PASSWORD_CREDENTIALS("password");

  String type;

  OAuthAuthorizationType(String type) {
    this.type = type;
  }

  public static OAuthAuthorizationType of(String type) {
    for (OAuthAuthorizationType oAuthAuthorizationType : OAuthAuthorizationType.values()) {
      if (oAuthAuthorizationType.type.equals(type)) {
        return oAuthAuthorizationType;
      }
    }
    throw new UnsupportedOperationException("Unsupported OAuth authorization type: " + type);
  }

  public String type() {
    return type;
  }
}
