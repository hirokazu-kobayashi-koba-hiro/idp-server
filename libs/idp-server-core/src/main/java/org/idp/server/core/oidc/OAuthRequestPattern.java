package org.idp.server.core.oidc;

/** OAuthRequestPattern */
public enum OAuthRequestPattern {
  NORMAL,
  REQUEST_OBJECT,
  REQUEST_URI;

  public boolean isRequestParameter() {
    return this == REQUEST_OBJECT || this == REQUEST_URI;
  }
}
