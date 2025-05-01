package org.idp.server.core.oidc;

/** AuthorizationProfile */
public enum AuthorizationProfile {
  OAUTH2,
  OIDC,
  FAPI_BASELINE,
  FAPI_ADVANCE,
  UNDEFINED;

  public boolean isOAuth2() {
    return this == OAUTH2;
  }

  public boolean isOidc() {
    return this == OIDC;
  }

  public boolean isFapiBaseline() {
    return this == FAPI_BASELINE;
  }

  public boolean isFapiAdvance() {
    return this == FAPI_ADVANCE;
  }

  public boolean isDefined() {
    return this != UNDEFINED;
  }
}
