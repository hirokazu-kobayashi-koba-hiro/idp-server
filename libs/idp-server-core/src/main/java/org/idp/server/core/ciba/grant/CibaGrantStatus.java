package org.idp.server.core.ciba.grant;

public enum CibaGrantStatus {
  authorized, authorization_pending, access_denied;

  public boolean isAuthorized() {
    return this == authorized;
  }

  public boolean isAuthorizationPending() {
    return this == authorization_pending;
  }

  public boolean isAccessDenied() {
    return this == access_denied;
  }
}
