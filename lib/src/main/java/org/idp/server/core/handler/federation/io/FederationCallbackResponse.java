package org.idp.server.core.handler.federation.io;

import org.idp.server.core.oauth.identity.User;

public class FederationCallbackResponse {

  FederationCallbackStatus status;
  User user;

  public FederationCallbackResponse() {}

  public FederationCallbackResponse(FederationCallbackStatus status) {
    this.status = status;
  }

  public FederationCallbackResponse(FederationCallbackStatus status, User user) {
    this.status = status;
    this.user = user;
  }

  public FederationCallbackStatus status() {
    return status;
  }

  public User user() {
    return user;
  }
}
